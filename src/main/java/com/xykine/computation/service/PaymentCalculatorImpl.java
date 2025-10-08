package com.xykine.computation.service;

import com.xykine.computation.domain.LoanStatus;
import com.xykine.computation.dto.LoanFilter;
import com.xykine.computation.entity.*;
import com.xykine.computation.repo.TaxRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.xykine.payroll.model.*;

import org.xykine.payroll.model.enums.PaymentTypeEnum;
import com.xykine.computation.session.SessionCalculationObject;
import com.xykine.computation.utils.ComputationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static com.xykine.computation.utils.ComputationUtils.*;

@Service
@RequiredArgsConstructor
public class PaymentCalculatorImpl implements PaymentCalculator{

    private final SessionCalculationObject sessionCalculationObject;
    private final EmployeeMetadataService employeeMetadataService;
    private final CompanyMetadataService companyMetadataService;
    private final TaxRepo taxRepo;
    private final LoanService loanService;

    protected static final Logger LOGGER = LoggerFactory.getLogger(PaymentCalculatorImpl.class);

    @Override
    public PaymentInfo expandPaymentSettingsFromGrossAnnual(PaymentInfo paymentInfo) {
        String paymentDistributionJson = getCompanyPaymentDistributionJson(paymentInfo.getCompanyID());
        List<PaymentDistribution> paymentDistributionList =
                ComputationUtils.getPaymentDistribution(paymentDistributionJson);
        Set<PaymentSettingsResponse> paymentSettingsFromPaymentDistributionList =
                ComputationUtils.getExpandedPaymentDistribution(paymentInfo, paymentDistributionList);
        Set<PaymentSettingsResponse> originalSettingsList =
                paymentInfo.getPaymentSettings() != null
                        ? paymentInfo.getPaymentSettings()
                        : new HashSet<>();
        originalSettingsList.addAll(paymentSettingsFromPaymentDistributionList);
        paymentInfo.setPaymentSettings(originalSettingsList);
        return paymentInfo;
    }

    @Override
    public PaymentInfo applyExchange(PaymentInfo paymentInfo) {
        BigDecimal exchangeRate = paymentInfo.getExchangeInfo().getExchangeRate();
        paymentInfo.setBasicSalary(ComputationUtils.exchangeToLocalCurrency(exchangeRate, paymentInfo.getBasicSalary()));
        Set<PaymentSettingsResponse> paymentSettingsResponseSet = new HashSet<>();
        paymentInfo.getPaymentSettings()
                .stream()
                .filter(x -> x.getValue() != null)
                .forEach(x -> {
                    x.setValue(ComputationUtils.exchangeToLocalCurrency(exchangeRate, x.getValue()));
                    paymentSettingsResponseSet.add(x);
                });
        paymentInfo.setPaymentSettings(paymentSettingsResponseSet);
        return paymentInfo;
    }

    @Override
    public PaymentInfo addPersonalDeduction(PaymentInfo paymentInfo) {
        if (paymentInfo.isOffCycle()) {return paymentInfo;}
        LoanFilter loanFilter = new LoanFilter();
        loanFilter.setCompanyId(paymentInfo.getCompanyID());
        loanFilter.setEmployeeId(paymentInfo.getEmployeeID());
        loanFilter.setStatus(LoanStatus.APPROVED);
        Page<Loan> employeeLoansPage = loanService.getLoans(loanFilter, Pageable.unpaged());
        List<Loan> employeeLoansList = employeeLoansPage.getContent();
        Set<PaymentSettingsResponse> employeePersonalDeductionSet = ComputationUtils.getEmployeeDeductions(employeeLoansList);
        Set<PaymentSettingsResponse> originalSettingsList = paymentInfo.getPaymentSettings() != null ? paymentInfo.getPaymentSettings() : new HashSet<>();
        originalSettingsList.addAll(employeePersonalDeductionSet);
        paymentInfo.setPaymentSettings(originalSettingsList);
        return paymentInfo;
    }

    @Override
    public PaymentInfo harmoniseToAnnual(PaymentInfo paymentInfo) {
        // Determine multiplier from company settings, default YEARLY
        long multiplier =companyMetadataService.getByCompanyId(paymentInfo.getCompanyID())
                .map(CompanyMetadata::getPaymentEntryMode)
                .map(this::getMultiplier)
                .orElse(1L);
        Set<PaymentSettingsResponse> updatedSettings = paymentInfo.getPaymentSettings()
                .stream()
                .filter(x -> x.getValue() != null)
                .map(setting -> harmonisePaymentSetting(setting, multiplier))
                .collect(Collectors.toSet());
        paymentInfo.setPaymentSettings(updatedSettings);
        return paymentInfo;
    }

    /**
     * Harmonises a single payment setting into annual terms,
     * applying business rules for allowances and off-cycle payments.
     */
    private PaymentSettingsResponse harmonisePaymentSetting(PaymentSettingsResponse setting, long globalMultiplier) {
        String description = setting.getType().getDescription();

        if (description.contains("ALLOWANCE") || description.contains("BASIC SALARY")) {
            setting.setValue(ComputationUtils.harmoniseToAnnual(globalMultiplier, setting.getValue()));
            if (description.contains("HOUSING")) {
                setting.setType(PaymentTypeEnum.ALLOWANCE_ANNUAL_HOUSING);
            } else if (description.contains("TRANSPORT")) {
                setting.setType(PaymentTypeEnum.ALLOWANCE_ANNUAL_TRANSPORT);
            } else if (description.contains("BASIC SALARY")) {
                setting.setType(PaymentTypeEnum.BASIC_SALARY_ANNUAL);
            }
            else {
                setting.setType(PaymentTypeEnum.ALLOWANCE_ANNUAL);
            }
        }
        else if (description.contains("OFF CYCLE")) {
            long customMultiplier = getMultiplier(setting.getSalaryFrequency());
            setting.setValue(ComputationUtils.harmoniseToAnnual(customMultiplier, setting.getValue()));
            setting.setType(PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT);
        }
        else if (description.contains("DEDUCTION")) {
            // leave deductions as is
        }
        return setting;
    }

    @Override
    public PaymentInfo computeGrossPay(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> grossPayMap = new HashMap<>();
        insertRecurrentPaymentMap(grossPayMap, paymentInfo);
        BigDecimal total = getTotal(grossPayMap);
        grossPayMap.put(MapKeys.GROSS_PAY, total);
        paymentInfo.setGrossPay(grossPayMap);
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeNonTaxableIncomeExempt(PaymentInfo paymentInfo) {

        if (isContract(paymentInfo)) {
            return paymentInfo;
        }

        if (paymentInfo.isOffCycle()) {
            return computeNonTaxableIncomeExemptForOffCycle(paymentInfo);
        }

        PaymentFrequencyEnum salaryFrequency = getSalaryFrequency(paymentInfo);
        int unpaidDays = paymentInfo.getNumberOfDaysOfUnpaidAbsence();

        BigDecimal basicSalary = paymentInfo.getPaymentSettings().stream()
                .filter(x -> x.getType() == PaymentTypeEnum.BASIC_SALARY_ANNUAL)
                .map(PaymentSettingsResponse::getValue)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        Map<String, BigDecimal> nonTaxableIncomeExemptMap = new HashMap<>();
        Map<String, BigDecimal> pension = new HashMap<>();
        Map<String, BigDecimal> nhf = new HashMap<>();

        EmployeeMetadata employeeMetadata = getEmployeeMetaData(paymentInfo);
        boolean isPensioned = employeeMetadata.isPensioned();
        // === Pension ===
        BigDecimal pensionFund = isPensioned ? getAllowanceForEmployee(paymentInfo).stream()
                .filter(x -> x.isPensionable()
                        || x.getType() == PaymentTypeEnum.ALLOWANCE_ANNUAL_HOUSING
                        || x.getType() == PaymentTypeEnum.ALLOWANCE_ANNUAL_TRANSPORT)
                .map(PaymentSettingsResponse::getValue)
                .reduce(basicSalary, BigDecimal::add) : BigDecimal.ZERO;

        BigDecimal employeePension = isPensioned ? ComputationUtils.roundToTwoDecimalPlaces(
                sessionCalculationObject.getComputationConstants().get("pensionFundPercent")
                        .multiply(pensionFund)) : BigDecimal.ZERO;

        nonTaxableIncomeExemptMap.put(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION,
                ComputationUtils.prorate(employeePension, unpaidDays, salaryFrequency));
        pension.put(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION,
                ComputationUtils.prorate(employeePension, unpaidDays, salaryFrequency));
        BigDecimal voluntaryPensionContribution =  getEmployeeMetaData(paymentInfo).getVoluntaryPensionContribution();
        pension.put("VOLUNTARY PENSION CONTRIBUTION", voluntaryPensionContribution);

        BigDecimal employerPensionContribution = ComputationUtils.roundToTwoDecimalPlaces(
                sessionCalculationObject.getComputationConstants().get("employerPensionContributionPercent")
                        .multiply(pensionFund));

        pension.put(MapKeys.EMPLOYER_PENSION_CONTRIBUTION,
                ComputationUtils.prorate(employerPensionContribution, unpaidDays, salaryFrequency));
        pension.put(MapKeys.TOTAL_PENSION_FOR_EMPLOYEE, getTotal(pension));

        ComputationUtils.updateReportSummary(paymentInfo, sessionCalculationObject,
                MapKeys.TOTAL_EMPLOYER_PENSION_CONTRIBUTION,
                ComputationUtils.prorate(employerPensionContribution, unpaidDays, salaryFrequency));

        // === NHF ===
        BigDecimal nationalHousingFund = isNHFSubscribed(paymentInfo)
                ? ComputationUtils.roundToTwoDecimalPlaces(
                sessionCalculationObject.getComputationConstants().get("nationalHousingFundPercent")
                        .multiply(basicSalary))
                : BigDecimal.ZERO;

        BigDecimal nhfValue = ComputationUtils.prorate(nationalHousingFund, unpaidDays, salaryFrequency);
        nonTaxableIncomeExemptMap.put(MapKeys.NATIONAL_HOUSING_FUND, nhfValue);
        nhf.put(MapKeys.NATIONAL_HOUSING_FUND, nhfValue);
        paymentInfo.setNhf(nhf);

        paymentInfo = computeNonTaxableIncomeExemptForMFB(paymentInfo, nationalHousingFund);
        paymentInfo.setPension(pension);
        return paymentInfo;
    }

    public PaymentInfo computeNonTaxableIncomeExemptForMFB(PaymentInfo paymentInfo, BigDecimal nationalHousingFund) {
        if (isContract(paymentInfo)) {
            return paymentInfo;
        }
        Map<String, BigDecimal> nonTaxableIncomeExemptMap = new HashMap<>();
        BigDecimal annualGrossSalary = paymentInfo.getBasicSalary();
        BigDecimal voluntaryPensionContribution = getEmployeeMetaData(paymentInfo).getVoluntaryPensionContribution();
        BigDecimal annualVoluntaryPensionContribution = voluntaryPensionContribution.multiply(BigDecimal.valueOf(12));
        BigDecimal customTaxReleifApplicable = getEmployeeMetaData(paymentInfo).getCustomTaxReliefApplicable();
        BigDecimal annualCustomTaxReleifApplicable = customTaxReleifApplicable.multiply(BigDecimal.valueOf(12));

        BigDecimal annualEmployeePensionAtEightPercent = ComputationUtils.roundToTwoDecimalPlaces(
                sessionCalculationObject.getComputationConstants().get("pensionFundPercent")
                        .multiply(annualGrossSalary));
        annualEmployeePensionAtEightPercent = ComputationUtils.roundToTwoDecimalPlaces(annualEmployeePensionAtEightPercent.multiply(BigDecimal.valueOf(0.3292)));
        annualEmployeePensionAtEightPercent = ComputationUtils.roundToTwoDecimalPlaces(annualEmployeePensionAtEightPercent.subtract(annualVoluntaryPensionContribution));
        BigDecimal grossPayForTaxPurpose = annualGrossSalary.subtract(annualEmployeePensionAtEightPercent).subtract(voluntaryPensionContribution);
        BigDecimal annualConsolidatedAllowance = getAnnualConsolidatedAllowance(grossPayForTaxPurpose);
        BigDecimal reliefAllowance = annualConsolidatedAllowance.add(annualEmployeePensionAtEightPercent).add(annualVoluntaryPensionContribution).add(nationalHousingFund).add(annualCustomTaxReleifApplicable);
        BigDecimal chargeableIncome = annualGrossSalary.subtract(reliefAllowance);

        nonTaxableIncomeExemptMap.put("CUSTOM TAX RELIEF APPLICABLE", customTaxReleifApplicable);
        nonTaxableIncomeExemptMap.put("GROSS PAY (TAX PURPOSE)", grossPayForTaxPurpose);
        nonTaxableIncomeExemptMap.put("ANNUAL CONSOLIDATED ALLOWANCE", annualConsolidatedAllowance);
        nonTaxableIncomeExemptMap.put("ANNUAL EMPLOYEE PENSION @ 8%", annualEmployeePensionAtEightPercent);
        nonTaxableIncomeExemptMap.put("RELIEF ALLOWANCE", reliefAllowance);
        nonTaxableIncomeExemptMap.put("CHARGEABLE INCOME", chargeableIncome);
        nonTaxableIncomeExemptMap.put("ANNUAL VOLUNTARY PENSION CONTRIBUTION", annualVoluntaryPensionContribution);

        paymentInfo.setTaxRelief(nonTaxableIncomeExemptMap);
        return paymentInfo;
    }

    private PaymentInfo computeNonTaxableIncomeExemptForOffCycle(PaymentInfo paymentInfo) {
        if (isContract(paymentInfo)) {
            return paymentInfo;
        }
        PaymentFrequencyEnum salaryFrequency = paymentInfo.isOffCycle() ? getOffCyclePaymentFrequency(paymentInfo) :  getSalaryFrequency(paymentInfo);
        Map<String, BigDecimal> nonTaxableIncomeExemptMap = new HashMap<>();
        Map<String, BigDecimal> nhf = new HashMap<>();
        nhf.put(MapKeys.NATIONAL_HOUSING_FUND, BigDecimal.ZERO);

        Map<String, BigDecimal> pension = new HashMap<>();
        pension.put(MapKeys.EMPLOYER_PENSION_CONTRIBUTION, BigDecimal.ZERO);
        pension.put(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION, BigDecimal.ZERO);

        BigDecimal grossIncomeForCRA  = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY);
        BigDecimal rawFXR = roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("craFraction")
                        .multiply(grossIncomeForCRA));
        if (rawFXR.compareTo(sessionCalculationObject.getComputationConstants().get("craCutOff")) == 1) {
            nonTaxableIncomeExemptMap.put(MapKeys.FIXED_CONSOLIDATED_RELIEF_ALLOWANCE, prorate(rawFXR, 0, salaryFrequency));
        } else {
            nonTaxableIncomeExemptMap.put(MapKeys.FIXED_CONSOLIDATED_RELIEF_ALLOWANCE, prorate(
                    BigDecimal.valueOf(200000),0, salaryFrequency));
        }

        BigDecimal variableCRA = roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("variableCRAFraction")
                        .multiply(grossIncomeForCRA));
        nonTaxableIncomeExemptMap.put(MapKeys.VARIABLE_CONSOLIDATED_RELIEF_ALLOWANCE, roundToTwoDecimalPlaces(prorate(variableCRA, 0, salaryFrequency)));

        BigDecimal total = getTotal(nonTaxableIncomeExemptMap);

        nonTaxableIncomeExemptMap.put(MapKeys.TOTAL_TAX_RELIEF, total);
        paymentInfo.setNhf(nhf);
        paymentInfo.setPension(pension);
        paymentInfo.setTaxRelief(nonTaxableIncomeExemptMap);

        return paymentInfo;
    }

    @Override
    public PaymentInfo prorateEarnings(PaymentInfo paymentInfo){
        PaymentFrequencyEnum salaryFrequency = paymentInfo.isOffCycle() ? getOffCyclePaymentFrequency(paymentInfo) :  getSalaryFrequency(paymentInfo);
        if (paymentInfo.isOffCycleActualValueSupplied())
            return paymentInfo;

        Map<String, BigDecimal> earningMap = paymentInfo.getGrossPay();
        earningMap.put(MapKeys.GROSS_PAY, BigDecimal.ZERO);

        for(Map.Entry<String, BigDecimal> entry : earningMap.entrySet()) {
            if (!entry.getKey().contains(MapKeys.GROSS_PAY))  {
                int unpaidDays = paymentInfo.isOffCycle() ? 0 : paymentInfo.getNumberOfDaysOfUnpaidAbsence();
                earningMap.put(entry.getKey(), prorate(entry.getValue(), unpaidDays, salaryFrequency));
            }
        }
        BigDecimal total = getTotal(earningMap);
        earningMap.put(MapKeys.GROSS_PAY, total);

        paymentInfo.setGrossPay(earningMap);;
        return paymentInfo;
    }

    @Override
    public PaymentInfo computePayeeTax(PaymentInfo paymentInfo) {
        if (isContract(paymentInfo)) {
            return paymentInfo;
        }
        String jsonTaxRule = taxRepo.findTaxRuleByCountry("NIGERIA");
        Map<String, BigDecimal> payeeTax = new HashMap<>();
        PaymentFrequencyEnum salaryFrequency = getSalaryFrequency(paymentInfo);
        BigDecimal chargeableIncome = paymentInfo.getTaxRelief().get("CHARGEABLE INCOME");
        payeeTax.put(MapKeys.TAXABLE_INCOME, chargeableIncome);
        BigDecimal monthlyPayeeTax = !paymentInfo.isOffCycle() ?
                ComputationUtils.prorate(ComputationUtils.getAnnualTaxAmount(chargeableIncome, jsonTaxRule), paymentInfo.getNumberOfDaysOfUnpaidAbsence(), salaryFrequency)
                :  ComputationUtils.getTaxAmount(paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY), jsonTaxRule);

        if (!paymentInfo.isOffCycle()) {
            payeeTax.put("ANNUAL PAYEE TAX", ComputationUtils.getAnnualTaxAmount(chargeableIncome, jsonTaxRule));
        }

        payeeTax.put(!paymentInfo.isOffCycle() ?  "MONTHLY PAYEE" : "Payee Tax on " + getOffCyclePaymentDetails(paymentInfo).getName(), monthlyPayeeTax);
        paymentInfo.setPayeeTax(payeeTax);
        updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_PAYEE_TAX,
                monthlyPayeeTax);
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeTotalDeduction(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> deductionMap = new HashMap<>();
        String payee_tax_key = "";
        if (!isContract(paymentInfo)) {
            if (paymentInfo.isOffCycle()) {
                payee_tax_key = "Payee Tax on " + getOffCyclePaymentDetails(paymentInfo).getName();
                deductionMap.put(payee_tax_key, paymentInfo.getPayeeTax().get(payee_tax_key));
                deductionMap.put(MapKeys.TOTAL_DEDUCTION, paymentInfo.getPayeeTax().get(payee_tax_key));
                updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_PERSONAL_DEDUCTION, paymentInfo.getPayeeTax().get(payee_tax_key));
                paymentInfo.setDeduction(deductionMap);
                return paymentInfo;
            }
        payee_tax_key = "MONTHLY PAYEE";
        deductionMap.put(payee_tax_key, paymentInfo.getPayeeTax().get(payee_tax_key));
        deductionMap.put(MapKeys.PENSION_FUND, paymentInfo.getPension().get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION));
        deductionMap.put(MapKeys.NATIONAL_HOUSING_FUND, paymentInfo.getNhf().get(MapKeys.NATIONAL_HOUSING_FUND));
        var deductions = getDeductionsForEmployee(paymentInfo);
        deductions
                .forEach(x -> {
                    deductionMap.put(x.getName(), x.getValue());
                    updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_PERSONAL_DEDUCTION, x.getValue());
                });
        BigDecimal voluntaryPensionContribution = getEmployeeMetaData(paymentInfo).getVoluntaryPensionContribution();
        deductionMap.put("VOLUNTARY PENSION CONTRIBUTION", voluntaryPensionContribution);
        updateReportSummary(paymentInfo, sessionCalculationObject, "TOTAL VOLUNTARY PENSION CONTRIBUTION", voluntaryPensionContribution);

        deductionMap.put(MapKeys.TOTAL_DEDUCTION, getTotal(deductionMap));
        paymentInfo.setDeduction(deductionMap);
        updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_EMPLOYEE_PENSION_CONTRIBUTION, paymentInfo.getPension().get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION));
        } else {
            BigDecimal contractorGross = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY);
            BigDecimal withHoldingTaxPercentage = sessionCalculationObject.getComputationConstants().get("withHoldingTax");
            BigDecimal withHoldingTaxAmount = ComputationUtils.roundToTwoDecimalPlaces(withHoldingTaxPercentage.multiply(contractorGross));
            deductionMap.put("WHT", withHoldingTaxAmount);
            deductionMap.put(MapKeys.TOTAL_DEDUCTION, getTotal(deductionMap));
            paymentInfo.setDeduction(deductionMap);
            updateReportSummary(paymentInfo, sessionCalculationObject, "TOTAL WITHHOLDING TAX", withHoldingTaxAmount);
        }
        return paymentInfo;
    }

    private Map<String, BigDecimal> insertRecurrentPaymentMap(Map<String, BigDecimal> earningMap, PaymentInfo paymentInfo){
        if (paymentInfo.isOffCycle()) {
            PaymentSettingsResponse paymentSettingsResponse = getOffCyclePaymentDetails(paymentInfo);
            earningMap.put(paymentSettingsResponse.getName(), paymentSettingsResponse.getValue());
        } else {
            Set<PaymentSettingsResponse> allowance = getAllowanceForEmployee(paymentInfo);
            allowance.forEach(x -> earningMap.put(x.getName(), x.getValue()));
        }
        return earningMap;
    }

    @Override
    public PaymentInfo computeNetPay(PaymentInfo paymentInfo) {
        if(paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY) != null) {
            ExchangeInfo exchangeInfo = paymentInfo.getExchangeInfo();
            BigDecimal exchangeRate = exchangeInfo.getExchangeRate();
            BigDecimal voluntaryPensionContribution =  !paymentInfo.isOffCycle() ? getEmployeeMetaData(paymentInfo).getVoluntaryPensionContribution() : BigDecimal.ZERO;
            BigDecimal netPay = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY).subtract(paymentInfo.getDeduction().get(MapKeys.TOTAL_DEDUCTION)).subtract(voluntaryPensionContribution);
            paymentInfo.setNetPay(roundToTwoDecimalPlaces(netPay.divide(exchangeRate, 0, RoundingMode.CEILING)));
            //Add net pay to summary
            updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_NET_PAY, netPay);
            //Add gross pay to summary
            updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_GROSS_PAY, paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY));
        }
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeTotalNHF(PaymentInfo paymentInfo) {
        if (isContract(paymentInfo)) {
            return paymentInfo;
        }
        if(paymentInfo.getNhf().get(MapKeys.NATIONAL_HOUSING_FUND) != null) {
            BigDecimal nhf = paymentInfo.getNhf().get(MapKeys.NATIONAL_HOUSING_FUND);
            updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_NHF, nhf);
        }
        return paymentInfo;
    }

    private BigDecimal getTotal(Map<String, BigDecimal> input){
        BigDecimal total = input.values().stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return roundToTwoDecimalPlaces(total);
    }

    private Set<PaymentSettingsResponse> getAllowanceForEmployee (PaymentInfo paymentInfo) {
        var paymentSettings = paymentInfo.getPaymentSettings();
        return  paymentSettings
                .stream()
                .filter(setting -> setting.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL) || setting.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL_TRANSPORT) || setting.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL_HOUSING)
                        || setting.getType().equals(PaymentTypeEnum.BASIC_SALARY_ANNUAL))
                .collect(Collectors.toSet());
    }

    private PaymentSettingsResponse getOffCyclePaymentDetails (PaymentInfo paymentInfo) {

        var paymentSettings = paymentInfo.getPaymentSettings();
        return paymentSettings
                .stream()
                .filter(setting -> setting.getType().equals(PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT))
                .findFirst().orElseGet(PaymentSettingsResponse::new);
    }

    private Set<PaymentSettingsResponse> getDeductionsForEmployee (PaymentInfo paymentInfo) {
        var paymentSettings = paymentInfo.getPaymentSettings();
        return paymentSettings.stream().filter(setting -> setting.getType().getDescription().contains("DEDUCTION")).collect(Collectors.toSet());
    }

    private long getMultiplier(PaymentFrequencyEnum paymentFrequencyEnum) {
        return switch (paymentFrequencyEnum) {
            case YEARLY -> 1L;
            case MONTHLY -> 12L;
            default -> 1L;
        };
    }

    private boolean isContract(PaymentInfo paymentInfo) {
        return Optional.ofNullable(getEmployeeMetaData(paymentInfo))
                .map(EmployeeMetadata::getEmployeeType)
                .orElse(EmployeeType.FULL_TIME) == EmployeeType.CONTRACT ;
    }

    private boolean isNHFSubscribed (PaymentInfo paymentInfo) {
        return Optional.ofNullable(getEmployeeMetaData(paymentInfo))
                .map(EmployeeMetadata::isNHFSubscribed)
                .orElse(false);
    }

    private BigDecimal getAnnualConsolidatedAllowance(BigDecimal grossAnnual) {
        if (grossAnnual == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal onePercent = grossAnnual.multiply(BigDecimal.valueOf(0.01));
        BigDecimal twentyPercent = grossAnnual.multiply(BigDecimal.valueOf(0.20));
        BigDecimal threshold = BigDecimal.valueOf(200000);

        BigDecimal result;

        if (onePercent.compareTo(threshold) > 0) {
            result = onePercent.add(twentyPercent);
        } else {
            result = threshold.add(twentyPercent);
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    private EmployeeMetadata getEmployeeMetaData(PaymentInfo paymentInfo) {
        EmployeeMetadata defaultEmployeeMetadata = EmployeeMetadata.builder()
                .voluntaryPensionContribution(BigDecimal.ZERO)
                .isNHFSubscribed(false)
                .employeeType(EmployeeType.FULL_TIME)
                .customTaxReliefApplicable(BigDecimal.ZERO)
                .isPensioned(true)
                .build();
        return employeeMetadataService.getByEmployeeId(paymentInfo.getEmployeeID()).orElse(defaultEmployeeMetadata);
    }

    private PaymentFrequencyEnum getSalaryFrequency(PaymentInfo paymentInfo) {
        CompanyMetadata metadata = companyMetadataService.getByCompanyId(paymentInfo.getCompanyID()).orElse(null);
        return (metadata != null && metadata.getSalaryFrequency() != null)
                ? metadata.getSalaryFrequency()
                : PaymentFrequencyEnum.MONTHLY;
    }

    private String getCompanyPaymentDistributionJson(String companyId) {
        CompanyMetadata metadata = companyMetadataService.getByCompanyId(companyId).orElse(null);
        return (metadata != null && metadata.getPaymentDistribution() != null)
                ? metadata.getPaymentDistribution()
                : null;
    }

    private PaymentFrequencyEnum getOffCyclePaymentFrequency(PaymentInfo paymentInfo) {
        return paymentInfo.getPaymentSettings()
                .stream()
                .filter(setting -> setting.getType() == PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT)
                .map(PaymentSettingsResponse::getSalaryFrequency)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(PaymentFrequencyEnum.YEARLY);

    }
}
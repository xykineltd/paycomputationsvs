package com.xykine.computation.service;

import com.xykine.computation.domain.LoanStatus;
import com.xykine.computation.dto.LoanFilter;
import com.xykine.computation.entity.*;
import com.xykine.computation.repo.PaymentSettingMetadataRepo;
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
import java.time.LocalDate;
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
    private final PaymentSettingMetadataRepo paymentSettingMetadataRepo;


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

        // TODO we need to throw proper error if the payment settings is not present for both custom or the company metadata payment distribution
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
        Page<Loan> employeeLoansPage = loanService.getLoans(loanFilter, LocalDate.parse(paymentInfo.getStartDate()), Pageable.unpaged());
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
         //TODO review with moruff

            long customMultiplier = getMultiplier(setting.getSalaryFrequency());
            setting.setValue(ComputationUtils.harmoniseToAnnual(customMultiplier, setting.getValue()));
            setting.setType(PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT);
            setting.setSalaryFrequency(PaymentFrequencyEnum.YEARLY);
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

        if (!paymentInfo.isOffCycle()) {
            grossPayMap.put("Gross Salary", total);
        } else {
            grossPayMap.put("Gross Salary", BigDecimal.ZERO);
        }

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

        BigDecimal grossPay = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY);

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

        nonTaxableIncomeExemptMap.put(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION, employeePension);
        pension.put(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION, employeePension);

        BigDecimal voluntaryPensionContribution =  getEmployeeMetaData(paymentInfo).getVoluntaryPensionContribution();
        pension.put("Voluntary Pension Contribution", voluntaryPensionContribution);

        BigDecimal employerPensionContribution = ComputationUtils.roundToTwoDecimalPlaces(
                sessionCalculationObject.getComputationConstants().get("employerPensionContributionPercent")
                        .multiply(pensionFund));

        pension.put(MapKeys.EMPLOYER_PENSION_CONTRIBUTION, employerPensionContribution);
        pension.put(MapKeys.TOTAL_PENSION_FOR_EMPLOYEE, getTotal(pension));

        ComputationUtils.updateReportSummary(paymentInfo, sessionCalculationObject,
                MapKeys.TOTAL_EMPLOYER_PENSION_CONTRIBUTION, employerPensionContribution);

        // === NHF ===
        BigDecimal nationalHousingFund = isNHFSubscribed(paymentInfo)
                ? ComputationUtils.roundToTwoDecimalPlaces(
                sessionCalculationObject.getComputationConstants().get("nationalHousingFundPercent")
                        .multiply(grossPay))
                : BigDecimal.ZERO;

        BigDecimal nhfValue = ComputationUtils.prorate(nationalHousingFund, unpaidDays, salaryFrequency, paymentInfo.getStartDate());
        nonTaxableIncomeExemptMap.put(MapKeys.NATIONAL_HOUSING_FUND, nhfValue);
        nhf.put(MapKeys.NATIONAL_HOUSING_FUND, nhfValue);
        paymentInfo.setNhf(nhf);

        Tax tax = taxRepo.findTaxByCountryAndActiveIsTrue("NIGERIA");

        LOGGER.debug("Tax Version: {}", tax);
        String taxVersion = taxRepo.findTaxByCountryAndActiveIsTrue("NIGERIA").getVersion().toString();

        paymentInfo = computeNonTaxableIncomeExemptForMFBNewTaxLaw(paymentInfo, nationalHousingFund);

        paymentInfo.setPension(pension);
        return paymentInfo;
    }

public PaymentInfo computeNonTaxableIncomeExemptForMFBNewTaxLaw(PaymentInfo paymentInfo, BigDecimal nationalHousingFund) {
    Map<String, BigDecimal> nonTaxableIncomeExemptMap = new HashMap<>();
    if (isContract(paymentInfo)) {
        return paymentInfo;
    }

    BigDecimal annualGrossSalary = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY).multiply(BigDecimal.valueOf(12L));
    BigDecimal voluntaryPensionContribution = getEmployeeMetaData(paymentInfo).getVoluntaryPensionContribution();
    BigDecimal annualVoluntaryPensionContribution = voluntaryPensionContribution.multiply(BigDecimal.valueOf(12));
    BigDecimal customTaxReliefApplicable = getEmployeeMetaData(paymentInfo).getCustomTaxReliefApplicable(); // Tax relief is updated every cycle. Apply as inputted. No annualisation required.
    BigDecimal rentAllowance = getEmployeeMetaData(paymentInfo).getRentAllowance();

    BigDecimal annualEmployeePensionAtEightPercent = isIntern(paymentInfo) ? BigDecimal.ZERO : ComputationUtils.roundToTwoDecimalPlaces(
            ComputationUtils.prorate(sessionCalculationObject.getComputationConstants().get("pensionFundPercent").multiply(paymentInfo.getBasicSalary()),
                    paymentInfo.getNumberOfDaysOfUnpaidAbsence(), PaymentFrequencyEnum.YEARLY, paymentInfo.getStartDate())
    );

    annualEmployeePensionAtEightPercent = isIntern(paymentInfo) || !isPensionable(paymentInfo) ? BigDecimal.ZERO : ComputationUtils.roundToTwoDecimalPlaces(annualEmployeePensionAtEightPercent.multiply(BigDecimal.valueOf(0.3292)));
    BigDecimal reliefAllowance = nationalHousingFund
            .add(annualEmployeePensionAtEightPercent)
            .add(annualVoluntaryPensionContribution)
            .add(rentAllowance)
            .add(customTaxReliefApplicable);

    BigDecimal chargeableIncome = annualGrossSalary.subtract(reliefAllowance);
    BigDecimal callAllowance = paymentInfo.getGrossPay().getOrDefault("Call/Data Allowance", BigDecimal.ZERO);
    BigDecimal monthlyChargeable = chargeableIncome.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP).subtract(callAllowance);
    monthlyChargeable = monthlyChargeable.subtract(getTotalMonthlyTaxFreeAllowance(paymentInfo));

    nonTaxableIncomeExemptMap.put("ANNUAL EMPLOYEE PENSION @ 8%", annualEmployeePensionAtEightPercent);
    nonTaxableIncomeExemptMap.put("RENT RELIEF", rentAllowance);
    nonTaxableIncomeExemptMap.put("ANNUAL NHF ALLOWANCE", nationalHousingFund);
    nonTaxableIncomeExemptMap.put("MONTHLY CHARGEABLE INCOME", monthlyChargeable);
    nonTaxableIncomeExemptMap.put("Annual Voluntary Pension Contribution", annualVoluntaryPensionContribution);
    nonTaxableIncomeExemptMap.put("MONTHLY RELIEF", reliefAllowance.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP));
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

    // Do not apply Tax Releif for Off-Cycle

    BigDecimal rawFXR = roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("craFraction")
            .multiply(grossIncomeForCRA));
    if (rawFXR.compareTo(sessionCalculationObject.getComputationConstants().get("craCutOff")) == 1) {
        nonTaxableIncomeExemptMap.put(MapKeys.FIXED_CONSOLIDATED_RELIEF_ALLOWANCE, prorate(rawFXR, 0, salaryFrequency, paymentInfo.getStartDate()));
    } else {
        nonTaxableIncomeExemptMap.put(MapKeys.FIXED_CONSOLIDATED_RELIEF_ALLOWANCE, prorate(
                BigDecimal.valueOf(200000),0, salaryFrequency, paymentInfo.getStartDate()));
    }

    BigDecimal variableCRA = roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("variableCRAFraction")
            .multiply(grossIncomeForCRA));
    nonTaxableIncomeExemptMap.put(MapKeys.VARIABLE_CONSOLIDATED_RELIEF_ALLOWANCE, roundToTwoDecimalPlaces(prorate(variableCRA, 0, salaryFrequency, paymentInfo.getStartDate())));
    BigDecimal total = getTotal(nonTaxableIncomeExemptMap);
    nonTaxableIncomeExemptMap.put(MapKeys.TOTAL_TAX_RELIEF, total);

    //nonTaxableIncomeExemptMap.put("MONTHLY CHARGEABLE INCOME", roundToTwoDecimalPlaces(grossIncomeForCRA));

    LocalDate start = LocalDate.parse(paymentInfo.getStartDate());

    List<String> nonTaxableEntries =
            paymentSettingMetadataRepo.findByEmployeeIdAndTaxable(paymentInfo.getEmployeeID(), false)
                    .stream()
                    .filter(x -> !x.getStartDate().isAfter(start) && !x.getEndDate().isBefore(start))
                    .map(PaymentSettingMetaData::getPaymentName)
                    .toList();

    BigDecimal nonTaxableValue = paymentInfo.getPaymentSettings()
            .stream()
            .filter(x -> nonTaxableEntries.contains(x.getName()))
            .map(PaymentSettingsResponse::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);


    BigDecimal totalChargeable = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY).subtract(nonTaxableValue);
    nonTaxableIncomeExemptMap.put("MONTHLY CHARGEABLE INCOME", totalChargeable.divide(
            BigDecimal.valueOf(12),
            2,
            RoundingMode.HALF_UP
    ));

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
                earningMap.put(entry.getKey(), prorate(entry.getValue(), unpaidDays, salaryFrequency, paymentInfo.getStartDate()));
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

        List<PaymentSettingMetaData> settingsMetadata = paymentSettingMetadataRepo.findByEmployeeId(paymentInfo.getEmployeeID());
        Map<String, BigDecimal> payeeTax = new HashMap<>();

        if (paymentInfo.isOffCycle() && paymentInfo.getPaymentSettings() != null) {

            PaymentSettingsResponse offCyclePayment = paymentInfo.getPaymentSettings().stream()
                    .filter(payment -> "OFF_CYCLE_PAYMENT_AMOUNT".equalsIgnoreCase(payment.getType().toString()))
                    .findFirst().orElse(null);

            if (offCyclePayment == null) {
                return paymentInfo;
            }

            if (!sessionCalculationObject.isOffCycleTaxable()) {
                payeeTax.put(!paymentInfo.isOffCycle() ?  "PAYE" : "Paye Tax on " + getOffCyclePaymentDetails(paymentInfo).getName(), BigDecimal.ZERO);
                paymentInfo.setPayeeTax(payeeTax);
                return paymentInfo;
            }
        }

        Tax taxInfo = taxRepo.findTaxByCountryAndActiveIsTrue("NIGERIA");

        PaymentFrequencyEnum salaryFrequency = getSalaryFrequency(paymentInfo);
        BigDecimal chargeableIncome = paymentInfo.getTaxRelief().get("MONTHLY CHARGEABLE INCOME").multiply(BigDecimal.valueOf(12L));
        payeeTax.put(MapKeys.TAXABLE_INCOME, chargeableIncome);
        BigDecimal monthlyPayeeTax = !paymentInfo.isOffCycle() ?
                //ComputationUtils.getAnnualTaxAmount(chargeableIncome, taxInfo)
                ComputationUtils.prorate(ComputationUtils.getAnnualTaxAmount(chargeableIncome, taxInfo), 0, salaryFrequency, paymentInfo.getStartDate())
                :  ComputationUtils.getTaxAmount(paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY), taxInfo);

        if (!paymentInfo.isOffCycle()) {
            payeeTax.put("ANNUAL PAYE TAX", ComputationUtils.getAnnualTaxAmount(chargeableIncome, taxInfo));
        }

        payeeTax.put(!paymentInfo.isOffCycle() ?  "PAYE" : "Paye Tax on " + getOffCyclePaymentDetails(paymentInfo).getName(), monthlyPayeeTax);
        paymentInfo.setPayeeTax(payeeTax);
        updateReportSummary(paymentInfo, sessionCalculationObject, "Pay-As-You-Earn (PAYE)",
                monthlyPayeeTax);
        return paymentInfo;
    }


    private boolean isIntern(PaymentInfo paymentInfo) {
        return Optional.ofNullable(getEmployeeMetaData(paymentInfo))
                .map(EmployeeMetadata::getEmployeeType)
                .orElse(EmployeeType.FULL_TIME) == EmployeeType.INTERN ;
    }

    private boolean isPensionable(PaymentInfo paymentInfo) {
        return Optional.ofNullable(getEmployeeMetaData(paymentInfo))
                .map(EmployeeMetadata::isPensioned)
                .orElse(true);
    }

    @Override
    public PaymentInfo computeTotalDeduction(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> deductionMap = new HashMap<>();

        BigDecimal voluntaryPensionContribution = getEmployeeMetaData(paymentInfo).getVoluntaryPensionContribution();

        String payee_tax_key = "";
        if (!isContract(paymentInfo)) {
            if (paymentInfo.isOffCycle()) {
                payee_tax_key = "Paye Tax on " + getOffCyclePaymentDetails(paymentInfo).getName();
                deductionMap.put(payee_tax_key, paymentInfo.getPayeeTax().get(payee_tax_key));
                deductionMap.put("Total PAYE", paymentInfo.getPayeeTax().get(payee_tax_key));
                deductionMap.put(MapKeys.TOTAL_DEDUCTION, paymentInfo.getPayeeTax().get(payee_tax_key));
                updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_PERSONAL_DEDUCTION, paymentInfo.getPayeeTax().get(payee_tax_key));
                paymentInfo.setDeduction(deductionMap);
                return paymentInfo;
            }
        payee_tax_key = "PAYE";
        deductionMap.put(payee_tax_key, paymentInfo.getPayeeTax().get(payee_tax_key));
        deductionMap.put("Total PAYE", paymentInfo.getPayeeTax().get(payee_tax_key));
        deductionMap.put(MapKeys.PENSION_FUND, paymentInfo.getPension().get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION));
        deductionMap.put(MapKeys.NATIONAL_HOUSING_FUND, paymentInfo.getNhf().get(MapKeys.NATIONAL_HOUSING_FUND));
        var deductions = getDeductionsForEmployee(paymentInfo);
        deductions
                .forEach(x -> {
                    deductionMap.put(x.getName(), x.getValue());
                    updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_PERSONAL_DEDUCTION, x.getValue());
                });
        deductionMap.put("Voluntary Pension Contribution", voluntaryPensionContribution);
        updateReportSummary(paymentInfo, sessionCalculationObject, "Total Voluntary Pension Contribution", voluntaryPensionContribution);

        deductionMap.put(MapKeys.TOTAL_DEDUCTION, getTotal(deductionMap));
        paymentInfo.setDeduction(deductionMap);
        updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_EMPLOYEE_PENSION_CONTRIBUTION, paymentInfo.getPension().get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION));
        } else {
            BigDecimal contractorGross = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY);
            BigDecimal withHoldingTaxPercentage = sessionCalculationObject.getComputationConstants().get("withHoldingTax");
            BigDecimal withHoldingTaxAmount = ComputationUtils.roundToTwoDecimalPlaces(withHoldingTaxPercentage.multiply(contractorGross));
            deductionMap.put("WHT", withHoldingTaxAmount);

            var deductions = getDeductionsForEmployee(paymentInfo);
            deductions
                    .forEach(x -> {
                        deductionMap.put(x.getName(), x.getValue());
                        updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_PERSONAL_DEDUCTION, x.getValue());
                    });

            deductionMap.put(MapKeys.TOTAL_DEDUCTION, getTotal(deductionMap));
            paymentInfo.setDeduction(deductionMap);

            Map<String, BigDecimal> nonTaxableIncomeExemptMap = new HashMap<>();
            nonTaxableIncomeExemptMap.put("MONTHLY CHARGEABLE INCOME",contractorGross);
            paymentInfo.setTaxRelief(nonTaxableIncomeExemptMap);

            updateReportSummary(paymentInfo, sessionCalculationObject, "TotaL Withholding Tax", withHoldingTaxAmount);
        }
        return paymentInfo;
    }

    private Map<String, BigDecimal> insertRecurrentPaymentMap(Map<String, BigDecimal> earningMap, PaymentInfo paymentInfo){
        PaymentFrequencyEnum salaryFrequency = paymentInfo.isOffCycle() ? getOffCyclePaymentFrequency(paymentInfo) :  getSalaryFrequency(paymentInfo);
        int numberOfUnpaidDays = paymentInfo.getNumberOfDaysOfUnpaidAbsence();
        if (paymentInfo.isOffCycle()) {
            PaymentSettingsResponse paymentSettingsResponse = getOffCyclePaymentDetails(paymentInfo);
            earningMap.put(paymentSettingsResponse.getName(), paymentSettingsResponse.getValue());
        } else {
            Set<PaymentSettingsResponse> allowance = getAllowanceForEmployee(paymentInfo);
            allowance.stream()
                    .map(entry ->
                            {
                                entry.setValue(entry.getType() != PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT ?
                                        prorate(entry.getValue(), numberOfUnpaidDays, salaryFrequency, paymentInfo.getStartDate()) :
                                        prorate(entry.getValue(), 0, salaryFrequency, paymentInfo.getStartDate()));
                                return entry;
                            }
                    )
                    .forEach(x -> earningMap.put(x.getName(), x.getValue()));
        }
        return earningMap;
    }

    @Override
    public PaymentInfo computeNetPay(PaymentInfo paymentInfo) {
        if(paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY) != null) {
            ExchangeInfo exchangeInfo = paymentInfo.getExchangeInfo();
            BigDecimal exchangeRate = exchangeInfo.getExchangeRate();
            BigDecimal netPay = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY).subtract(paymentInfo.getDeduction().get(MapKeys.TOTAL_DEDUCTION));
            paymentInfo.setNetPay(
                    roundToTwoDecimalPlaces(
                            netPay.divide(exchangeRate, 2, RoundingMode.CEILING)
                    )
            );
            updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_NET_PAY, netPay);
            //Add gross pay to summary
            updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_GROSS_PAY, paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY));
        }
        return paymentInfo;
    }

    private static BigDecimal getNetPay(PaymentInfo paymentInfo, BigDecimal voluntaryPensionContribution) {
        BigDecimal deduction = paymentInfo.getDeduction().get(MapKeys.TOTAL_DEDUCTION);
        BigDecimal grossPay = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY);

        if (deduction == null) {
            deduction = BigDecimal.ZERO;
        }

        return grossPay.subtract(deduction).subtract(voluntaryPensionContribution);
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

    private BigDecimal getTotal(Map<String, BigDecimal> input) {
        BigDecimal total = input.entrySet().stream()
                .filter(e -> !"Total PAYE".equalsIgnoreCase(e.getKey()))
                .filter(e -> !"Gross Salary".equalsIgnoreCase(e.getKey()))
                .filter(e -> !"Taxable Gross".equalsIgnoreCase(e.getKey()))

                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return roundToTwoDecimalPlaces(total);
    }

    private Set<PaymentSettingsResponse> getAllowanceForEmployee (PaymentInfo paymentInfo) {
        var paymentSettings = paymentInfo.getPaymentSettings();
        return  paymentSettings
                .stream()
                .filter(setting -> setting.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL)
                        || setting.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL_TRANSPORT)
                        || setting.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL_HOUSING)
                        || setting.getType().equals(PaymentTypeEnum.BASIC_SALARY_ANNUAL)
                        || setting.getType().equals(PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT)
                )
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
        //TODO temp fix to set default to 1 , remove after fix
        if (paymentFrequencyEnum == null)
            return 1L;
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
                .rentAllowance(BigDecimal.valueOf(500000L))
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

    private BigDecimal getTotalMonthlyTaxFreeAllowance(PaymentInfo paymentInfo) {
            return
                    paymentSettingMetadataRepo.findByEmployeeIdAndCompanyId(paymentInfo.getEmployeeID(), paymentInfo.getCompanyID())
                            .stream()
                            .filter(Objects::nonNull)
                            .filter(setting -> !setting.getStartDate().isAfter(LocalDate.parse(paymentInfo.getStartDate())) && !setting.getEndDate().isBefore(LocalDate.parse(paymentInfo.getEndDate())))
                            .filter(setting -> "ALLOWANCE".equalsIgnoreCase(setting.getPaymentType()))
                            .filter(setting -> !setting.getTaxable())
                            .filter(setting -> setting.getPaymentAmount() != null)
                            .map(value -> value.getPaymentAmount())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
package com.xykine.computation.service;

import org.xykine.payroll.model.*;

import org.xykine.payroll.model.enums.PaymentFrequencyEnum;
import org.xykine.payroll.model.enums.PaymentTypeEnum;
import com.xykine.computation.session.SessionCalculationObject;
import com.xykine.computation.utils.ComputationUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentCalculatorImpl implements PaymentCalculator{

    private final SessionCalculationObject sessionCalculationObject;
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCalculatorImpl.class);

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
    public PaymentInfo harmoniseToAnnual(PaymentInfo paymentInfo) {
        AtomicLong multiplier = new AtomicLong(1L);

        paymentInfo.setBasicSalary(ComputationUtils.harmoniseToAnnual(multiplier.get(), paymentInfo.getBasicSalary()));
        Set<PaymentSettingsResponse> paymentSettingsResponseSet = new HashSet<>();

        // annualise all allowances
        paymentInfo.getPaymentSettings()
                .stream()
                .filter(x -> x.getValue() != null && (x.getType().getDescription().contains("ALLOWANCE") || x.getType().equals(PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT)))
                .forEach(x -> {

                    if (paymentInfo.getSalaryFrequency() != null)
                        multiplier.set(getMultiplier(paymentInfo.getSalaryFrequency().getDescription()));

                    x.setValue(ComputationUtils.harmoniseToAnnual(multiplier.get(), x.getValue()));
                        if (x.getType().getDescription().contains("HOUSING")) {
                            x.setType(PaymentTypeEnum.ALLOWANCE_ANNUAL_HOUSING);
                        } else  if (x.getType().getDescription().contains("TRANSPORT")) {
                            x.setType(PaymentTypeEnum.ALLOWANCE_ANNUAL_TRANSPORT);
                        } else  if (x.getType().getDescription().contains("OFF CYCLE")) {
                            x.setType(PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT);
                        }  else {
                            x.setType(PaymentTypeEnum.ALLOWANCE_ANNUAL);
                        }
                    paymentSettingsResponseSet.add(x);
                });
        // leave personal deduction as is.
        paymentInfo.getPaymentSettings()
                        .stream()
                        .filter(x -> x.getValue() != null && x.getType().getDescription().contains("DEDUCTION"))
                        .forEach(x -> {paymentSettingsResponseSet.add(x);
                        });
        paymentInfo.setPaymentSettings(paymentSettingsResponseSet);
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeGrossPay(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> grossPayMap = new HashMap<>();
        grossPayMap = insertRecurrentPaymentMap(grossPayMap, paymentInfo);
        BigDecimal total = getTotal(grossPayMap);
        grossPayMap.put(MapKeys.GROSS_PAY, total);
        paymentInfo.setGrossPay(grossPayMap);
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeNonTaxableIncomeExempt(PaymentInfo paymentInfo) {
        PaymentFrequencyEnum salaryFrequency = paymentInfo.getSalaryFrequency();
        LOGGER.info("isOffCycle ===> {}", paymentInfo.isOffCycle());
        LOGGER.info("gross ===> {}", paymentInfo.getGrossPay());

        if (paymentInfo.isOffCycle())
            return computeNonTaxableIncomeExemptForOffCycle(paymentInfo);

        Map<String, BigDecimal> nonTaxableIncomeExemptMap = new HashMap<>();
        Map<String, BigDecimal> nhf = new HashMap<>();
        Map<String, BigDecimal> pension = new HashMap<>();

        int numberOfUnpaidDays = paymentInfo.getNumberOfDaysOfUnpaidAbsence();
        BigDecimal basicSalary = paymentInfo.getBasicSalary();

        BigDecimal employeePensionFund = getAllowanceForEmployee(paymentInfo)
                .stream()
                .filter(x -> x.isPensionable() || x.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL_HOUSING) || x.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL_TRANSPORT))
                .map(PaymentSettingsResponse::getValue)
                .reduce(basicSalary, BigDecimal::add);
        BigDecimal employeePension = ComputationUtils
                .roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("pensionFundPercent")
                        .multiply(employeePensionFund));

        nonTaxableIncomeExemptMap.put(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION, ComputationUtils.prorate(employeePension,numberOfUnpaidDays, salaryFrequency));
        pension.put(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION, ComputationUtils.prorate(employeePension, numberOfUnpaidDays, salaryFrequency));

        BigDecimal employerPensionContribution = getAllowanceForEmployee(paymentInfo)
                .stream()
                .filter(x -> x.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL_HOUSING) || x.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL_TRANSPORT))
                .map(PaymentSettingsResponse::getValue)
                .reduce(basicSalary, BigDecimal::add);

        employerPensionContribution = ComputationUtils
                .roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("employerPensionContributionPercent")
                        .multiply(employerPensionContribution));

        pension.put(MapKeys.EMPLOYER_PENSION_CONTRIBUTION, ComputationUtils.prorate(employerPensionContribution, numberOfUnpaidDays, salaryFrequency));
        pension.put(MapKeys.TOTAL_PENSION_FOR_EMPLOYEE, getTotal(pension));

        ComputationUtils.updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_EMPLOYER_PENSION_CONTRIBUTION, ComputationUtils.prorate(employerPensionContribution,  numberOfUnpaidDays, salaryFrequency));

        BigDecimal nationalHousingFund = ComputationUtils
                .roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("nationalHousingFundPercent")
                        .multiply(basicSalary));
        BigDecimal nhfValue = ComputationUtils.prorate(nationalHousingFund, numberOfUnpaidDays, salaryFrequency);
        nonTaxableIncomeExemptMap.put(MapKeys.NATIONAL_HOUSING_FUND, ComputationUtils.prorate(nationalHousingFund,numberOfUnpaidDays, salaryFrequency));
        nhf.put(MapKeys.NATIONAL_HOUSING_FUND, nhfValue);
        paymentInfo.setNhf(nhf);

        BigDecimal grossIncomeForCRA  = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY).subtract(employeePension).subtract(nationalHousingFund);

        BigDecimal rawFXR = ComputationUtils
                .roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("craFraction")
                        .multiply(grossIncomeForCRA));
        if (rawFXR.compareTo(sessionCalculationObject.getComputationConstants().get("craCutOff")) == 1) {
            nonTaxableIncomeExemptMap.put(MapKeys.FIXED_CONSOLIDATED_RELIEF_ALLOWANCE, ComputationUtils.prorate(rawFXR, numberOfUnpaidDays, salaryFrequency));
        } else {
            nonTaxableIncomeExemptMap.put(MapKeys.FIXED_CONSOLIDATED_RELIEF_ALLOWANCE, ComputationUtils.prorate(
                    BigDecimal.valueOf(200000),numberOfUnpaidDays, salaryFrequency));
        }

        BigDecimal variableCRA = ComputationUtils
                .roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("variableCRAFraction")
                        .multiply(grossIncomeForCRA));
        nonTaxableIncomeExemptMap.put(MapKeys.VARIABLE_CONSOLIDATED_RELIEF_ALLOWANCE, ComputationUtils
                .roundToTwoDecimalPlaces(ComputationUtils.prorate(variableCRA, numberOfUnpaidDays, salaryFrequency)));

        BigDecimal total = getTotal(nonTaxableIncomeExemptMap);

        nonTaxableIncomeExemptMap.put(MapKeys.TOTAL_TAX_RELIEF, total);
        paymentInfo.setTaxRelief(nonTaxableIncomeExemptMap);
        paymentInfo.setPension(pension);
        return paymentInfo;
    }

    private PaymentInfo computeNonTaxableIncomeExemptForOffCycle(PaymentInfo paymentInfo) {
//        LOGGER.info("paymentInfo ===> {}", paymentInfo.getPaymentSettings());
        PaymentFrequencyEnum salaryFrequency = paymentInfo.getSalaryFrequency();

        Map<String, BigDecimal> nonTaxableIncomeExemptMap = new HashMap<>();
        Map<String, BigDecimal> nhf = new HashMap<>();
        nhf.put(MapKeys.NATIONAL_HOUSING_FUND, BigDecimal.ZERO);

        Map<String, BigDecimal> pension = new HashMap<>();
        pension.put(MapKeys.EMPLOYER_PENSION_CONTRIBUTION, BigDecimal.ZERO);
        pension.put(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION, BigDecimal.ZERO);


        BigDecimal grossIncomeForCRA  = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY);
        BigDecimal rawFXR = ComputationUtils
                .roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("craFraction")
                        .multiply(grossIncomeForCRA));
        if (rawFXR.compareTo(sessionCalculationObject.getComputationConstants().get("craCutOff")) == 1) {
            nonTaxableIncomeExemptMap.put(MapKeys.FIXED_CONSOLIDATED_RELIEF_ALLOWANCE, ComputationUtils.prorate(rawFXR, 0, salaryFrequency));
        } else {
            nonTaxableIncomeExemptMap.put(MapKeys.FIXED_CONSOLIDATED_RELIEF_ALLOWANCE, ComputationUtils.prorate(
                    BigDecimal.valueOf(200000),0, salaryFrequency));
        }

        BigDecimal variableCRA = ComputationUtils
                .roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("variableCRAFraction")
                        .multiply(grossIncomeForCRA));
        nonTaxableIncomeExemptMap.put(MapKeys.VARIABLE_CONSOLIDATED_RELIEF_ALLOWANCE, ComputationUtils
                .roundToTwoDecimalPlaces(ComputationUtils.prorate(variableCRA, 0, salaryFrequency)));

        BigDecimal total = getTotal(nonTaxableIncomeExemptMap);

        nonTaxableIncomeExemptMap.put(MapKeys.TOTAL_TAX_RELIEF, total);
        paymentInfo.setNhf(nhf);
        paymentInfo.setPension(pension);
        paymentInfo.setTaxRelief(nonTaxableIncomeExemptMap);

        return paymentInfo;
    }

    @Override
    public PaymentInfo prorateEarnings(PaymentInfo paymentInfo){
        PaymentFrequencyEnum salaryFrequency = paymentInfo.getSalaryFrequency();
        if (paymentInfo.isOffCycleActualValueSupplied())
            return paymentInfo;

        Map<String, BigDecimal> earningMap = paymentInfo.getGrossPay();
        earningMap.put(MapKeys.GROSS_PAY, BigDecimal.ZERO);

        for(Map.Entry<String, BigDecimal> entry : earningMap.entrySet()) {
            if (!entry.getKey().contains(MapKeys.GROSS_PAY))  {
                earningMap.put(entry.getKey(), ComputationUtils.prorate(entry.getValue(),
                        paymentInfo.getNumberOfDaysOfUnpaidAbsence(), salaryFrequency));
            }
        }
        BigDecimal total = getTotal(earningMap);
        earningMap.put(MapKeys.GROSS_PAY, total);

        paymentInfo.setGrossPay(earningMap);;
        return paymentInfo;
    }

    @Override
    public PaymentInfo computePayeeTax(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> payeeTax = new HashMap<>();
        BigDecimal taxableIncome = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY).subtract(paymentInfo.getTaxRelief().get(MapKeys.TOTAL_TAX_RELIEF));
        payeeTax.put(MapKeys.TAXABLE_INCOME, taxableIncome);

        BigDecimal empPayeeTax = ComputationUtils.getTaxAmount(taxableIncome, sessionCalculationObject);

        payeeTax.put(MapKeys.PAYEE_TAX, empPayeeTax);
        paymentInfo.setPayeeTax(payeeTax);

        ComputationUtils.updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_PAYEE_TAX,
                empPayeeTax);
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeTotalDeduction(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> deductionMap = new HashMap<>();

        deductionMap.put(MapKeys.PENSION_FUND, paymentInfo.getTaxRelief().get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION));
        deductionMap.put(MapKeys.PAYEE_TAX, paymentInfo.getPayeeTax().get(MapKeys.PAYEE_TAX));
        deductionMap.put(MapKeys.NATIONAL_HOUSING_FUND, paymentInfo.getNhf().get(MapKeys.NATIONAL_HOUSING_FUND));

        var deductions = getDeductionsForEmployee(paymentInfo);

        deductions.stream()
                .filter(PaymentSettingsResponse::isActive)
                .forEach(x -> {
                    deductionMap.put(x.getName(), x.getValue());
                    ComputationUtils.updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_PERSONAL_DEDUCTION, x.getValue());
                });

        deductionMap.put(MapKeys.TOTAL_DEDUCTION, getTotal(deductionMap));
        paymentInfo.setDeduction(deductionMap);

        ComputationUtils.updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_EMPLOYEE_PENSION_CONTRIBUTION, paymentInfo.getTaxRelief().get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION));
        return paymentInfo;
    }

    private Map<String, BigDecimal> insertRecurrentPaymentMap(Map<String, BigDecimal> earningMap, PaymentInfo paymentInfo){
        if (paymentInfo.isOffCycle()) {
            earningMap.put(MapKeys.OFF_CYCLE_PAYMENT, getOffCyclePaymentAmountForEmployee(paymentInfo).getValue());
            return earningMap;
        }
        earningMap.put(MapKeys.BASIC_SALARY, paymentInfo.getBasicSalary());

        Set<PaymentSettingsResponse> allowance = getAllowanceForEmployee(paymentInfo);
        allowance.stream()
                .filter(PaymentSettingsResponse::isActive)
                .forEach(x -> {
                    earningMap.put(x.getName(), x.getValue());
                });
        LOGGER.debug("earningMap ==> {}", earningMap);
        return earningMap;
    }

    @Override
    public PaymentInfo computeNetPay(PaymentInfo paymentInfo) {
        if(paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY) != null) {
            ExchangeInfo exchangeInfo = paymentInfo.getExchangeInfo();
            BigDecimal exchangeRate = exchangeInfo.getExchangeRate();
            BigDecimal netPay = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY).subtract(paymentInfo.getDeduction().get(MapKeys.TOTAL_DEDUCTION));
            paymentInfo.setNetPay(ComputationUtils.roundToTwoDecimalPlaces(netPay.divide(exchangeRate, 2, RoundingMode.CEILING)));
            //Add net pay to summary
            ComputationUtils.updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_NET_PAY, netPay);
            //Add gross pay to summary
            ComputationUtils.updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_GROSS_PAY, paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY));
        }
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeTotalNHF(PaymentInfo paymentInfo) {
        if(paymentInfo.getNhf().get(MapKeys.NATIONAL_HOUSING_FUND) != null) {
            BigDecimal nhf = paymentInfo.getNhf().get(MapKeys.NATIONAL_HOUSING_FUND);
            ComputationUtils.updateReportSummary(paymentInfo, sessionCalculationObject, MapKeys.TOTAL_NHF, nhf);
        }
        return paymentInfo;
    }

    private BigDecimal getTotal(Map<String, BigDecimal> input){
        BigDecimal total = BigDecimal.ZERO;
        for(Map.Entry<String, BigDecimal> entry : input.entrySet()) {
            BigDecimal value = entry.getValue() != null ? entry.getValue() : BigDecimal.ZERO;
            total = total.add(value);
        }
        return ComputationUtils.roundToTwoDecimalPlaces(total);
    }

    private Set<PaymentSettingsResponse> getAllowanceForEmployee (PaymentInfo paymentInfo) {
        var paymentSettings = paymentInfo.getPaymentSettings();
        return  paymentSettings
                .stream()
                .filter(setting -> setting.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL) || setting.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL_TRANSPORT) || setting.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL_HOUSING))
                .collect(Collectors.toSet());
    }

    private PaymentSettingsResponse getOffCyclePaymentAmountForEmployee (PaymentInfo paymentInfo) {
        var paymentSettings = paymentInfo.getPaymentSettings();
        LOGGER.debug("paymentSettings ==> {}", paymentSettings);
        return paymentSettings
                .stream()
                .filter(setting -> setting.getType().equals(PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT))
                .findFirst().orElseGet(PaymentSettingsResponse::new);
    }

    private Set<PaymentSettingsResponse> getDeductionsForEmployee (PaymentInfo paymentInfo) {
        var paymentSettings = paymentInfo.getPaymentSettings();
        return paymentSettings.stream().filter(setting -> setting.getType().getDescription().contains("DEDUCTION")).collect(Collectors.toSet());
    }

    private Long getMultiplier(String description) {
        Long multiplier = null;
        switch (description) {
            case "Yearly" -> multiplier = 1L;
            case "Monthly" -> multiplier = 12L;
            case "Weekly" -> multiplier = 48L;
            case "Bi-weekly" -> multiplier = 24L;
            default -> multiplier = 1L;
        }
        return multiplier;
    }
}
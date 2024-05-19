package com.xykine.computation.service;

import com.xykine.computation.model.MapKeys;
import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.model.PaymentSettings;

import com.xykine.computation.model.enums.PaymentTypeEnum;
import com.xykine.computation.session.SessionCalculationObject;
import com.xykine.computation.utils.ComputationUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class PaymentCalculatorImpl implements PaymentCalculator{


    private final SessionCalculationObject sessionCalculationObject;
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCalculatorImpl.class);

    @Override
    public PaymentInfo computeGrossPay(PaymentInfo paymentInfo) {
        LOGGER.info("paymentInfo====> {}", paymentInfo);
        Map<String, BigDecimal> grossPayMap = new HashMap<>();
        grossPayMap = insertRecurrentPaymentMap(grossPayMap, paymentInfo);
        BigDecimal total = getTotal(grossPayMap);
        grossPayMap.put(MapKeys.GROSS_PAY, total);
        paymentInfo.setGrossPay(grossPayMap);
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeNonTaxableIncomeExempt(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> nonTaxableIncomeExemptMap = new HashMap<>();
        Map<String, BigDecimal> nhf = new HashMap<>();
        Map<String, BigDecimal> pension = new HashMap<>();


        int numberOfUnpaidDays = paymentInfo.getNumberOfDaysOfUnpaidAbsence();
        var basePaymentAmount =
                ComputationUtils
                .getPaymentValueFromPaymentSetting(getOffCyclePaymentAmountForEmployee(paymentInfo))
                        .add(ComputationUtils.getPaymentValueFromBaseSalary(paymentInfo.getBasicSalary()));

        BigDecimal employeePensionFund = getAllowanceForEmployee(paymentInfo)
                .stream()
                .filter(x -> x.isPensionable() || x.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL_HOUSING) || x.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL_TRANSPORT))
//                .map(PaymentSettings::getValue)
                .map(p -> {
                    LOGGER.info("line55 {} --- {}", p.getName(), p.getValue());
                    return p.getValue();
                })
                .reduce(basePaymentAmount, BigDecimal::add);

        BigDecimal employeePension = ComputationUtils
                .roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("pensionFundPercent")
                        .multiply(employeePensionFund));

        nonTaxableIncomeExemptMap.put(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION, ComputationUtils.prorate(employeePension,
                numberOfUnpaidDays, paymentInfo.isOffCycleActualValueSupplied()));
        pension.put(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION, ComputationUtils.prorate(employeePension,
                numberOfUnpaidDays, paymentInfo.isOffCycleActualValueSupplied()));

        BigDecimal employerPensionContribution = getAllowanceForEmployee(paymentInfo)
                .stream()
                .filter(x -> x.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL_HOUSING) || x.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL_TRANSPORT))
                .map(PaymentSettings::getValue)
                .reduce(basePaymentAmount, BigDecimal::add);

        employerPensionContribution = ComputationUtils
                .roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("employerPensionContributionPercent")
                        .multiply(employerPensionContribution));


        pension.put(MapKeys.EMPLOYER_PENSION_CONTRIBUTION, ComputationUtils.prorate(employerPensionContribution,
                numberOfUnpaidDays, paymentInfo.isOffCycleActualValueSupplied()));
        pension.put(MapKeys.TOTAL_PENSION_FOR_EMPLOYEE, getTotal(pension));

        ComputationUtils.updateReportSummary(sessionCalculationObject, MapKeys.TOTAL_EMPLOYER_PENSION_CONTRIBUTION,
                ComputationUtils.prorate(employerPensionContribution,
                        numberOfUnpaidDays, paymentInfo.isOffCycleActualValueSupplied()));


        BigDecimal nationalHousingFund = ComputationUtils
                .roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("nationalHousingFundPercent")
                        .multiply(basePaymentAmount));

        LOGGER.info("basePaymentAmount {}", basePaymentAmount);
        LOGGER.info("nationalHousingFund {}", basePaymentAmount);

        BigDecimal nhfValue = ComputationUtils.prorate(nationalHousingFund,
                numberOfUnpaidDays, paymentInfo.isOffCycleActualValueSupplied());
        nonTaxableIncomeExemptMap.put(MapKeys.NATIONAL_HOUSING_FUND, ComputationUtils.prorate(nationalHousingFund,
                numberOfUnpaidDays, paymentInfo.isOffCycleActualValueSupplied()));
        nhf.put(MapKeys.NATIONAL_HOUSING_FUND, nhfValue);
        paymentInfo.setNhf(nhf);

        BigDecimal grossIncomeForCRA  = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY).subtract(employeePension).subtract(nationalHousingFund);

        BigDecimal rawFXR = ComputationUtils
                .roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("craFraction")
                        .multiply(grossIncomeForCRA));
        if (rawFXR.compareTo(sessionCalculationObject.getComputationConstants().get("craCutOff")) == 1) {
            nonTaxableIncomeExemptMap.put(MapKeys.FIXED_CONSOLIDATED_RELIEF_ALLOWANCE, ComputationUtils.prorate(rawFXR,
                    numberOfUnpaidDays, paymentInfo.isOffCycleActualValueSupplied()));
        } else {
            nonTaxableIncomeExemptMap.put(MapKeys.FIXED_CONSOLIDATED_RELIEF_ALLOWANCE, ComputationUtils.prorate(
                    BigDecimal.valueOf(200000),
                    numberOfUnpaidDays, paymentInfo.isOffCycleActualValueSupplied()));
        }


        BigDecimal variableCRA = ComputationUtils
                .roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("variableCRAFraction")
                        .multiply(grossIncomeForCRA));
        nonTaxableIncomeExemptMap.put(MapKeys.VARIABLE_CONSOLIDATED_RELIEF_ALLOWANCE, ComputationUtils
                .roundToTwoDecimalPlaces(ComputationUtils.prorate(variableCRA,
                        numberOfUnpaidDays,
                        paymentInfo.isOffCycleActualValueSupplied())));

        BigDecimal total = getTotal(nonTaxableIncomeExemptMap);

        nonTaxableIncomeExemptMap.put(MapKeys.TOTAL_TAX_RELIEF, total);
        paymentInfo.setTaxRelief(nonTaxableIncomeExemptMap);
        paymentInfo.setPension(pension);
        return paymentInfo;
    }

    @Override
    public PaymentInfo prorateEarnings(PaymentInfo paymentInfo){
        Map<String, BigDecimal> earningMap = paymentInfo.getGrossPay();
        earningMap.put(MapKeys.GROSS_PAY, BigDecimal.ZERO);

        for(Map.Entry<String, BigDecimal> entry : earningMap.entrySet()) {
            if (!entry.getKey().contains(MapKeys.GROSS_PAY))  {
                earningMap.put(entry.getKey(), ComputationUtils.prorate(entry.getValue(),
                        paymentInfo.getNumberOfDaysOfUnpaidAbsence(),
                        paymentInfo.isOffCycleActualValueSupplied()));
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
        final BigDecimal empPayeeTaxValue = empPayeeTax.signum() == -1 ? BigDecimal.valueOf(0.0) : empPayeeTax;

        payeeTax.put(MapKeys.PAYEE_TAX, empPayeeTaxValue);
        paymentInfo.setPayeeTax(payeeTax);

        ComputationUtils.updateReportSummary(sessionCalculationObject, MapKeys.TOTAL_PAYEE_TAX,
                empPayeeTaxValue);
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
                .filter(PaymentSettings::isActive)
                        .forEach(x -> {
                            deductionMap.put(x.getName(), x.getValue());
                            ComputationUtils.updateReportSummary(sessionCalculationObject, MapKeys.TOTAL_PERSONAL_DEDUCTION, x.getValue());
                        });

        deductionMap.put(MapKeys.TOTAL_DEDUCTION, getTotal(deductionMap));
        paymentInfo.setDeduction(deductionMap);

        ComputationUtils.updateReportSummary(sessionCalculationObject, MapKeys.TOTAL_EMPLOYEE_PENSION_CONTRIBUTION, paymentInfo.getTaxRelief().get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION));
        return paymentInfo;
    }

    private Map<String, BigDecimal> insertRecurrentPaymentMap(Map<String, BigDecimal> earningMap, PaymentInfo paymentInfo){

        var basicSalaryValue =
                paymentInfo.getBasicSalary() == null ? BigDecimal.valueOf(0.0) : paymentInfo.getBasicSalary();
        earningMap.put(MapKeys.BASIC_SALARY, basicSalaryValue);

        var paymentSettings = getOffCyclePaymentAmountForEmployee(paymentInfo);
        var offCyclePaymentValue = paymentSettings.getValue() == null ? BigDecimal.valueOf(0.0) : paymentSettings.getValue();
        earningMap.put(MapKeys.OFF_CYCLE_PAYMENT, offCyclePaymentValue);

//        LOGGER.info("paymentSettings.getValue()+++{}",  paymentSettings.getValue());
//        LOGGER.info("paymentInfo.getBasicSalary()+++{}", paymentInfo.getBasicSalary());
//        earningMap.put(MapKeys.BASIC_SALARY, paymentSettings.getValue());
        var allowance = getAllowanceForEmployee(paymentInfo);
        allowance.stream()
                .filter(PaymentSettings::isActive)
                .forEach(x -> {
                    earningMap.put(x.getName(), x.getValue());
                });

        LOGGER.info("earningMap+++, {}", earningMap);

        return earningMap;
    }

    @Override
    public PaymentInfo computeNetPay(PaymentInfo paymentInfo) {
        if(paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY) != null) {
            BigDecimal netPay = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY).subtract(paymentInfo.getDeduction().get(MapKeys.TOTAL_DEDUCTION));
            paymentInfo.setNetPay(netPay);

            //Add net pay to summary
            ComputationUtils.updateReportSummary(sessionCalculationObject, MapKeys.TOTAL_NET_PAY, netPay);
            //Add gross pay to summary
            ComputationUtils.updateReportSummary(sessionCalculationObject, MapKeys.TOTAL_GROSS_PAY, paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY));
        }
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeTotalNHF(PaymentInfo paymentInfo) {
        if(paymentInfo.getNhf().get(MapKeys.NATIONAL_HOUSING_FUND) != null) {
            BigDecimal nhf = paymentInfo.getNhf().get(MapKeys.NATIONAL_HOUSING_FUND);
            ComputationUtils.updateReportSummary(sessionCalculationObject, MapKeys.TOTAL_NHF, nhf);
        }
        return paymentInfo;
    }

    private BigDecimal getTotal(Map<String, BigDecimal> input){
        BigDecimal total = BigDecimal.ZERO;

        for(Map.Entry<String, BigDecimal> entry : input.entrySet()) {
            total = total.add(entry.getValue());
        }
        return ComputationUtils.roundToTwoDecimalPlaces(total);
    }

    private Set<PaymentSettings> getAllowanceForEmployee (PaymentInfo paymentInfo) {
        var paymentSettings = paymentInfo.getPaymentSettings();
        var list =  paymentSettings
                .stream()
                .filter(setting -> setting.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL) || setting.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL_TRANSPORT) || setting.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL_HOUSING))
                .collect(Collectors.toSet());
        LOGGER.info("list+++ {}", list);
        return list;
    }

    private PaymentSettings getBasicSalaryForEmployee (PaymentInfo paymentInfo) {
        var paymentSettings = paymentInfo.getPaymentSettings();
        return paymentSettings
                .stream()
                .filter(setting -> setting.getType().equals(PaymentTypeEnum.BASIC_SALARY_ANNUAL))
                .findFirst().orElseGet(PaymentSettings::new);
    }

    private PaymentSettings getOffCyclePaymentAmountForEmployee (PaymentInfo paymentInfo) {
        var paymentSettings = paymentInfo.getPaymentSettings();
        return paymentSettings
                .stream()
                .filter(setting -> setting.getType().equals(PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT))
                .findFirst().orElseGet(PaymentSettings::new);
    }

    private Set<PaymentSettings> getDeductionsForEmployee (PaymentInfo paymentInfo) {
        var paymentSettings = paymentInfo.getPaymentSettings();
        return paymentSettings.stream().filter(setting -> setting.getType().equals(PaymentTypeEnum.DEDUCTION_MONTHLY)).collect(Collectors.toSet());
    }
}

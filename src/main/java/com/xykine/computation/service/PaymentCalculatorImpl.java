package com.xykine.computation.service;

import com.xykine.computation.model.MapKeys;
import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.model.PaymentSettings;


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
        int numberOfUnpaidDays = paymentInfo.getNumberOfDaysOfUnpaidAbsence();

        BigDecimal pensionFund = getAllowanceForEmployee(paymentInfo)
                .stream()
                .filter(x -> x.isPensionable() || x.getName().contains(MapKeys.TRANSPORT) || x.getName().contains(MapKeys.HOUSING))
                        .map(PaymentSettings::getValue)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
        pensionFund = pensionFund.add(paymentInfo.getBasicSalary());

        BigDecimal employeePension = ComputationUtils
                .roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("pensionFundPercent")
                        .multiply(pensionFund));
        nonTaxableIncomeExemptMap.put(MapKeys.EMPLOYEE_PENSION, ComputationUtils.prorate(employeePension, numberOfUnpaidDays));

        BigDecimal nationalHousingFund = ComputationUtils
                .roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("nationalHousingFundPercent")
                        .multiply(paymentInfo.getBasicSalary()));
        nonTaxableIncomeExemptMap.put(MapKeys.NATIONAL_HOUSING_FUND, ComputationUtils.prorate(nationalHousingFund, numberOfUnpaidDays));

        BigDecimal grossIncomeForCRA  = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY).subtract(employeePension).subtract(nationalHousingFund);

        BigDecimal rawFXR = ComputationUtils
                .roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("craFraction")
                        .multiply(grossIncomeForCRA));

        if (rawFXR.compareTo(sessionCalculationObject.getComputationConstants().get("craCutOff")) == -1) {
            nonTaxableIncomeExemptMap.put(MapKeys.FIXED_CONSOLIDATED_RELIEF_ALLOWANCE, ComputationUtils.prorate(rawFXR, numberOfUnpaidDays));
        } else {
            nonTaxableIncomeExemptMap.put(MapKeys.FIXED_CONSOLIDATED_RELIEF_ALLOWANCE, ComputationUtils.prorate(BigDecimal.valueOf(200000), numberOfUnpaidDays));
        }

        BigDecimal total = getTotal(nonTaxableIncomeExemptMap);
        nonTaxableIncomeExemptMap.put(MapKeys.TOTAL_TAX_RELIEF, total);
        paymentInfo.setTaxRelief(nonTaxableIncomeExemptMap);
        return paymentInfo;
    }

    @Override
    public PaymentInfo prorateEarnings(PaymentInfo paymentInfo){
        Map<String, BigDecimal> earningMap = paymentInfo.getGrossPay();
        earningMap.put(MapKeys.GROSS_PAY, BigDecimal.ZERO);
        for(Map.Entry<String, BigDecimal> entry : earningMap.entrySet()) {
            if (!entry.getKey().contains(MapKeys.GROSS_PAY))  {
                earningMap.put(entry.getKey(), ComputationUtils.prorate(entry.getValue(), paymentInfo.getNumberOfDaysOfUnpaidAbsence()));
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

        BigDecimal taxPercent = getTax(taxableIncome);

        BigDecimal empPayeeTax = ComputationUtils.roundToTwoDecimalPlaces(taxPercent.multiply(taxableIncome).divide(BigDecimal.valueOf(100)));;
        payeeTax.put(MapKeys.PAYEE_TAX, empPayeeTax);
        paymentInfo.setPayeeTax(payeeTax);

        BigDecimal totalPayeeTax = sessionCalculationObject.getSummary().get(MapKeys.TOTAL_PAYEE_TAX);
        totalPayeeTax = ComputationUtils.roundToTwoDecimalPlaces(totalPayeeTax.add(empPayeeTax));;
        sessionCalculationObject.getSummary().put(MapKeys.TOTAL_PAYEE_TAX, totalPayeeTax);
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeTotalDeduction(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> deductionMap = new HashMap<>();

        deductionMap.put(MapKeys.PENSION_FUND, paymentInfo.getTaxRelief().get(MapKeys.EMPLOYEE_PENSION));
        deductionMap.put(MapKeys.PAYEE_TAX, paymentInfo.getPayeeTax().get(MapKeys.PAYEE_TAX));

        var deductions = getDeductionsForEmployee(paymentInfo);
        deductions.stream()
                .filter(PaymentSettings::isActive)
                        .forEach(x -> {
                            deductionMap.put(x.getName(), x.getValue());

                            BigDecimal totalPersonalDeduction = sessionCalculationObject.getSummary().get(MapKeys.TOTAL_PERSONAL_DEDUCTION);
                            totalPersonalDeduction = totalPersonalDeduction.add(x.getValue());
                            sessionCalculationObject.getSummary().put(MapKeys.TOTAL_PERSONAL_DEDUCTION, totalPersonalDeduction);
                        });

        deductionMap.put(MapKeys.TOTAL_DEDUCTION, getTotal(deductionMap));
        paymentInfo.setDeduction(deductionMap);

        BigDecimal totalPensionFund = sessionCalculationObject.getSummary().get(MapKeys.TOTAL_PENSION_FUND);
        totalPensionFund = totalPensionFund.add(paymentInfo.getTaxRelief().get(MapKeys.EMPLOYEE_PENSION));
        sessionCalculationObject.getSummary().put(MapKeys.TOTAL_PENSION_FUND, totalPensionFund);

        return paymentInfo;
    }

    private Map<String, BigDecimal> insertRecurrentPaymentMap(Map<String, BigDecimal> earningMap, PaymentInfo paymentInfo){
        earningMap.put(MapKeys.BASIC_SALARY, paymentInfo.getBasicSalary());
        var allowance = getAllowanceForEmployee(paymentInfo);
        //LOGGER.info("employee allowances :  {} ",  allowance);
        allowance.stream()
                .filter(PaymentSettings::isActive)
                .forEach(x -> {
                    earningMap.put(x.getName(), x.getValue());
                });
        return earningMap;
    }



    @Override
    public PaymentInfo computeNetPay(PaymentInfo paymentInfo) {
        if(paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY) != null) {
            BigDecimal netPay = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY).subtract(paymentInfo.getDeduction().get(MapKeys.TOTAL_DEDUCTION));
            paymentInfo.setNetPay(netPay);

            BigDecimal totalNetPay = sessionCalculationObject.getSummary().get(MapKeys.TOTAL_NET_PAY);
            totalNetPay = totalNetPay.add(netPay);
            sessionCalculationObject.getSummary().put(MapKeys.TOTAL_NET_PAY, totalNetPay);
        }
        return paymentInfo;
    }


    private BigDecimal getTax(BigDecimal taxableIncome){
        Double taxableIncomeDouble = taxableIncome.doubleValue();

        if (taxableIncomeDouble <= 200000.0) {
            return sessionCalculationObject.getComputationConstants().get("TaxClassA");
        }
        if (taxableIncomeDouble > 200000.0 && taxableIncomeDouble >= 600000.0 ) {
            return sessionCalculationObject.getComputationConstants().get("TaxClassB");
        }
        if (taxableIncomeDouble > 200000.0 && taxableIncomeDouble >= 600000.0 ) {
            return sessionCalculationObject.getComputationConstants().get("TaxClassC");
        }
        if (taxableIncomeDouble > 600000.0 && taxableIncomeDouble >= 1100000.0 ) {
            return sessionCalculationObject.getComputationConstants().get("TaxClassD");
        }
        if (taxableIncomeDouble > 1100000.0 && taxableIncomeDouble >= 1600000.0 ) {
            return sessionCalculationObject.getComputationConstants().get("TaxClassE");
        }
        if (taxableIncomeDouble > 1600000.0 && taxableIncomeDouble >= 3200000.0 ) {
            return sessionCalculationObject.getComputationConstants().get("TaxClassF");
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal getTotal(Map<String, BigDecimal> input){
        BigDecimal total = BigDecimal.ZERO;

        for(Map.Entry<String, BigDecimal> entry : input.entrySet()) {
            total = total.add(entry.getValue());
        }
        return ComputationUtils.roundToTwoDecimalPlaces(total);
    }

    private Set<PaymentSettings> getAllowanceForEmployee (PaymentInfo paymentInfo) {
        var paymentSettings = paymentInfo.getEmployee().getPaymentSettings();
        return paymentSettings.stream().filter(setting -> setting.getType().equalsIgnoreCase("Allowance")).collect(Collectors.toSet());
    }

    private Set<PaymentSettings> getDeductionsForEmployee (PaymentInfo paymentInfo) {
        var paymentSettings = paymentInfo.getEmployee().getPaymentSettings();
        return paymentSettings.stream().filter(setting -> !setting.getType().equalsIgnoreCase("ALLOWANCE")).collect(Collectors.toSet());
    }
}

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
        Map<String, BigDecimal> nhf = new HashMap<>();
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

        var nhfValue = ComputationUtils.prorate(nationalHousingFund, numberOfUnpaidDays);
        nonTaxableIncomeExemptMap.put(MapKeys.NATIONAL_HOUSING_FUND, ComputationUtils.prorate(nationalHousingFund, numberOfUnpaidDays));
        nhf.put(MapKeys.NATIONAL_HOUSING_FUND, nhfValue);
        paymentInfo.setNhf(nhf);

        BigDecimal grossIncomeForCRA  = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY).subtract(employeePension).subtract(nationalHousingFund);

        BigDecimal rawFXR = ComputationUtils
                .roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("craFraction")
                        .multiply(grossIncomeForCRA));
        if (rawFXR.compareTo(sessionCalculationObject.getComputationConstants().get("craCutOff")) == 1) {
            nonTaxableIncomeExemptMap.put(MapKeys.FIXED_CONSOLIDATED_RELIEF_ALLOWANCE, ComputationUtils.prorate(rawFXR, numberOfUnpaidDays));
        } else {
            nonTaxableIncomeExemptMap.put(MapKeys.FIXED_CONSOLIDATED_RELIEF_ALLOWANCE, ComputationUtils.prorate(BigDecimal.valueOf(200000), numberOfUnpaidDays));
        }


        BigDecimal variableCRA = ComputationUtils
                .roundToTwoDecimalPlaces(sessionCalculationObject.getComputationConstants().get("variableCRAFraction")
                        .multiply(grossIncomeForCRA));
        nonTaxableIncomeExemptMap.put(MapKeys.VARIABLE_CONSOLIDATED_RELIEF_ALLOWANCE, ComputationUtils
                .roundToTwoDecimalPlaces(ComputationUtils.prorate(variableCRA, numberOfUnpaidDays)));

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


        BigDecimal empPayeeTax = ComputationUtils.getTaxAmount(taxableIncome, sessionCalculationObject);
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
        deductionMap.put(MapKeys.NATIONAL_HOUSING_FUND, paymentInfo.getNhf().get(MapKeys.NATIONAL_HOUSING_FUND));

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

            //Add gross pay to summary
            BigDecimal totalGrossPay = sessionCalculationObject.getSummary().get(MapKeys.TOTAL_GROSS_PAY);
            totalGrossPay = totalGrossPay.add(paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY));
            sessionCalculationObject.getSummary().put(MapKeys.TOTAL_GROSS_PAY, totalGrossPay);
        }
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeTotalNHF(PaymentInfo paymentInfo) {
        if(paymentInfo.getNhf().get(MapKeys.NATIONAL_HOUSING_FUND) != null) {
            BigDecimal totalNHF = sessionCalculationObject.getSummary().get(MapKeys.TOTAL_NHF);
            BigDecimal nhf = paymentInfo.getNhf().get(MapKeys.NATIONAL_HOUSING_FUND);

            totalNHF = totalNHF.add(nhf);
            sessionCalculationObject.getSummary().put(MapKeys.TOTAL_NHF, totalNHF);
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
        return paymentSettings.stream().filter(setting -> setting.getType().equalsIgnoreCase(MapKeys.ALLOWANCE)).collect(Collectors.toSet());
    }

    private Set<PaymentSettings> getDeductionsForEmployee (PaymentInfo paymentInfo) {
        var paymentSettings = paymentInfo.getPaymentSettings();
        return paymentSettings.stream().filter(setting -> setting.getType().equalsIgnoreCase(MapKeys.DEDUCTION)).collect(Collectors.toSet());
    }
}

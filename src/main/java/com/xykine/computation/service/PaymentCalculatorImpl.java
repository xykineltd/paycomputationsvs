package com.xykine.computation.service;

import com.xykine.computation.entity.Deductions;
import com.xykine.computation.model.Allowance;
import com.xykine.computation.model.MapKeys;
import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.model.PaymentSettings;
import com.xykine.computation.repo.*;

import com.xykine.computation.session.SessionCalculationObject;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class PaymentCalculatorImpl implements PaymentCalculator{

    private final TaxRepo taxRepo;
    private final DeductionRepo deductionRepo;
    private final SessionCalculationObject sessionCalculationObject;

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCalculatorImpl.class);

    // To do ==> complete implementation
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
//        BigDecimal basicHousingAndTransport = paymentInfo.getEmployee().allowances()
        BigDecimal basicHousingAndTransport = getAllowanceForEmployee(paymentInfo)
                .stream()
                .filter(x -> x.isActive() && x.getName().contains(MapKeys.HOUSING) || x.getName().contains(MapKeys.TRANSPORT))
                        .map(PaymentSettings::getValue)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
        basicHousingAndTransport = basicHousingAndTransport.add(paymentInfo.getBasicSalary());
        BigDecimal employeePension = BigDecimal.valueOf(0.08).multiply(basicHousingAndTransport).setScale(2, RoundingMode.CEILING);;
        nonTaxableIncomeExemptMap.put(MapKeys.EMPLOYEE_PENSION, employeePension);
        BigDecimal nationalHousingFund = BigDecimal.valueOf(0.025).multiply(paymentInfo.getBasicSalary()).setScale(2, RoundingMode.CEILING);;
        nonTaxableIncomeExemptMap.put(MapKeys.NATIONAL_HOUSING_FUND, nationalHousingFund);
        BigDecimal grossIncomeForCRA  = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY).subtract(employeePension).subtract(nationalHousingFund);
        BigDecimal rawFXR = BigDecimal.valueOf(0.01).multiply(grossIncomeForCRA).setScale(2, RoundingMode.CEILING);
        if (rawFXR.compareTo(BigDecimal.valueOf(200000)) == -1) {
            nonTaxableIncomeExemptMap.put(MapKeys.FIXED_CONSOLIDATED_RELIEF_ALLOWANCE, rawFXR);
        } else {
            nonTaxableIncomeExemptMap.put(MapKeys.FIXED_CONSOLIDATED_RELIEF_ALLOWANCE, BigDecimal.valueOf(200000));
        }
        BigDecimal total = getTotal(nonTaxableIncomeExemptMap);
        nonTaxableIncomeExemptMap.put(MapKeys.TOTAL_TAX_RELIEF, total);
        paymentInfo.setTaxRelief(nonTaxableIncomeExemptMap);
        return paymentInfo;
    }

    @Override
    public PaymentInfo computePayeeTax(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> payeeTax = new HashMap<>();
        BigDecimal taxableIncome = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY).subtract(paymentInfo.getTaxRelief().get(MapKeys.TOTAL_TAX_RELIEF));
        payeeTax.put(MapKeys.TAXABLE_INCOME, taxableIncome);
        String taxClass = getTaxClass(taxableIncome);
        BigDecimal taxPercent = taxRepo.findTaxByTaxClass(taxClass).getPercentage();
        BigDecimal empPayeeTax = taxPercent.multiply(taxableIncome).divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.CEILING);;
        payeeTax.put(MapKeys.PAYEE_TAX, empPayeeTax);
        paymentInfo.setPayeeTax(payeeTax);

        BigDecimal totalPayeeTax = sessionCalculationObject.getSummary().get(MapKeys.TOTAL_PAYEE_TAX);
        totalPayeeTax = totalPayeeTax.add(empPayeeTax).setScale(2, RoundingMode.CEILING);;
        sessionCalculationObject.getSummary().put(MapKeys.TOTAL_PAYEE_TAX, totalPayeeTax);
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeTotalDeduction(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> deductionMap = new HashMap<>();
        deductionMap.put(MapKeys.PENSION_FUND, paymentInfo.getTaxRelief().get(MapKeys.EMPLOYEE_PENSION));
        deductionMap.put(MapKeys.PAYEE_TAX, paymentInfo.getPayeeTax().get(MapKeys.PAYEE_TAX));

//        List<Deductions> personalDeduction = deductionRepo.findDeductionByEmployeeId(paymentInfo.getId());

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
//        Set<Allowance> allowance = paymentInfo.getEmployee().allowances();
        LOGGER.info("employee allowances :  {} ",  allowance);
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

    private BigDecimal prorateBasicSalary(PaymentInfo paymentInfo){
        int numberOfDaysOfUnpaidAbsence = paymentInfo.getNumberOfDaysOfUnpaidAbsence();
        if (numberOfDaysOfUnpaidAbsence == 0)
            return paymentInfo.getBasicSalary();
        // if numberOfDaysOfUnpaidAbsence is not 0, remove the daily wage equivalent multiplied by the number of unpaid absences
        BigDecimal dailyWage = paymentInfo.getBasicSalary().divide(BigDecimal.valueOf(21)).setScale(2, RoundingMode.CEILING);;  // To do ==> verify number of working days in the month
         return paymentInfo.getBasicSalary().subtract(dailyWage.multiply(BigDecimal.valueOf(numberOfDaysOfUnpaidAbsence))).setScale(2, RoundingMode.CEILING);
    }

    private String getTaxClass(BigDecimal taxableIncome){
        Double taxableIncomeDouble = taxableIncome.doubleValue();
        if (taxableIncomeDouble <= 200000.0) {
            return "A";
        }
        if (taxableIncomeDouble > 200000.0 && taxableIncomeDouble >= 600000.0 ) {
            return "B";
        }
        if (taxableIncomeDouble > 200000.0 && taxableIncomeDouble >= 600000.0 ) {
            return "C";
        }
        if (taxableIncomeDouble > 600000.0 && taxableIncomeDouble >= 1100000.0 ) {
            return "D";
        }
        if (taxableIncomeDouble > 1100000.0 && taxableIncomeDouble >= 1600000.0 ) {
            return "E";
        }
        if (taxableIncomeDouble > 1600000.0 && taxableIncomeDouble >= 3200000.0 ) {
            return "F";
        }
        return "not found";
    }

    private BigDecimal getTotal(Map<String, BigDecimal> input){
        BigDecimal total = BigDecimal.ZERO;

        for(Map.Entry<String, BigDecimal> entry : input.entrySet()) {
            total = total.add(entry.getValue());
        }
        return total.setScale(2, RoundingMode.CEILING);
    }

    private Set<PaymentSettings> getAllowanceForEmployee (PaymentInfo paymentInfo) {
        var paymentSettings = paymentInfo.getEmployee().getPaymentSettings();
        return paymentSettings.stream().filter(setting -> setting.getType().equals("Gross Pay")).collect(Collectors.toSet());
    }

    private Set<PaymentSettings> getDeductionsForEmployee (PaymentInfo paymentInfo) {
        var paymentSettings = paymentInfo.getEmployee().getPaymentSettings();
        return paymentSettings.stream().filter(setting -> !setting.getType().equals("Gross Pay")).collect(Collectors.toSet());
    }
}

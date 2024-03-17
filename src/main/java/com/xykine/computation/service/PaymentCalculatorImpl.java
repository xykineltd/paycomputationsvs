package com.xykine.computation.service;

import com.xykine.computation.entity.AllowanceAndOtherPayments;
import com.xykine.computation.entity.Deductions;
import com.xykine.computation.entity.Tax;
import com.xykine.computation.model.Allowance;
import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.model.TaxBearer;
import com.xykine.computation.model.wagetypegroup.Earnings;
import com.xykine.computation.repo.*;

import com.xykine.computation.session.SessionCalculationObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Service
@RequiredArgsConstructor
public class PaymentCalculatorImpl implements PaymentCalculator{

    private final TaxRepo taxRepo;
    private final DeductionRepo deductionRepo;
    private final PensionFundRepo pensionFundRepo;
    private final SessionCalculationObject sessionCalculationObject;
    private final MathContext tRounding = new MathContext(2, RoundingMode.HALF_EVEN);

    // To do ==> complete implementation
    @Override
    public PaymentInfo computeGrossPay(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> grossPayMap = new HashMap<>();
        grossPayMap = insertRecurrentPaymentMap(grossPayMap, paymentInfo);
        BigDecimal total = getTotal(grossPayMap);
        grossPayMap.put("Gross pay", total);
        paymentInfo.setGrossPay(grossPayMap);
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeNonTaxableIncomeExempt(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> nonTaxableIncomeExemptMap = new HashMap<>();
        BigDecimal basicHousingAndTransport = paymentInfo.getEmployee().allowances()
                .stream()
                .filter(x -> x.isActive() && x.name().contains("Housing") || x.name().contains("Transport"))
                        .map(x -> x.value())
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal employeePension = BigDecimal.valueOf(0.08).multiply(basicHousingAndTransport);
        nonTaxableIncomeExemptMap.put("Employee pension", employeePension);
        BigDecimal nationalHousingFund = BigDecimal.valueOf(0.025).multiply(paymentInfo.getBasicSalary());
        nonTaxableIncomeExemptMap.put("National Housing Fund", nationalHousingFund);
        BigDecimal grossIncomeForCRA  = paymentInfo.getGrossPay().get("Gross pay").subtract(employeePension).subtract(nationalHousingFund);
        BigDecimal rawFXR = BigDecimal.valueOf(0.01).multiply(grossIncomeForCRA);
        if (rawFXR.compareTo(BigDecimal.valueOf(200000)) == -1) {
            nonTaxableIncomeExemptMap.put("Fixed Consolidated Relief Allowance", rawFXR);
        } else {
            nonTaxableIncomeExemptMap.put("Fixed Consolidated Relief Allowance", BigDecimal.valueOf(200000));
        }
        BigDecimal total = getTotal(nonTaxableIncomeExemptMap);
        nonTaxableIncomeExemptMap.put("Total Tax Relief", total);
        paymentInfo.setTaxRelief(nonTaxableIncomeExemptMap);
        return paymentInfo;
    }

    @Override
    public PaymentInfo computePayeeTax(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> payeeTax = new HashMap<>();
        BigDecimal taxableIncome = paymentInfo.getGrossPay().get("Gross pay").subtract(paymentInfo.getTaxRelief().get("Total Tax Relief"));
        payeeTax.put("Taxable Income", taxableIncome);
        String taxClass = getTaxClass(taxableIncome);
        BigDecimal taxPercent = taxRepo.findTaxByTaxClass(taxClass).getPercentage();
        BigDecimal empPayeeTax = taxPercent.multiply(taxableIncome).divide(BigDecimal.valueOf(100));
        payeeTax.put("Payee Tax", empPayeeTax);
        paymentInfo.setPayeeTax(payeeTax);

        BigDecimal totalPayeeTax = sessionCalculationObject.getSummary().get("Total Payee Tax");
        totalPayeeTax = totalPayeeTax.add(empPayeeTax);
        sessionCalculationObject.getSummary().put("Total Payee Tax", totalPayeeTax);
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeTotalDeduction(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> deductionMap = new HashMap<>();
        deductionMap.put("Pension Fund", paymentInfo.getTaxRelief().get("Employee pension"));
        deductionMap.put("Payee Tax", paymentInfo.getPayeeTax().get("Payee Tax"));
        List<Deductions> personalDeduction = deductionRepo.findDeductionByEmployeeId(paymentInfo.getId());

        personalDeduction.stream()
                .filter(x -> x.getActive())
                        .forEach(x -> {
                            deductionMap.put(x.getDescription(), x.getAmount());

                            BigDecimal totalPersonalDeduction = sessionCalculationObject.getSummary().get("Total Personal Deduction");
                            totalPersonalDeduction = totalPersonalDeduction.add(x.getAmount());
                            sessionCalculationObject.getSummary().put("Total Personal Deduction", totalPersonalDeduction);
                        });

        deductionMap.put("Total Deduction", getTotal(deductionMap));
        paymentInfo.setDeduction(deductionMap);

        BigDecimal totalPensionFund = sessionCalculationObject.getSummary().get("Total Pension Fund");
        totalPensionFund = totalPensionFund.add(paymentInfo.getTaxRelief().get("Employee pension"));
        sessionCalculationObject.getSummary().put("Total Pension Fund", totalPensionFund);

        return paymentInfo;
    }

    private Map<String, BigDecimal> insertRecurrentPaymentMap(Map<String, BigDecimal> earningMap, PaymentInfo paymentInfo){
        earningMap.put("Basic salary", paymentInfo.getBasicSalary());
        Set<Allowance> allowance = paymentInfo.getEmployee().allowances();
        allowance.stream()
                .filter(x -> x.isActive())
                .forEach(x -> {
                    earningMap.put(x.name(), x.value());
                });
        return earningMap;
    }



    @Override
    public PaymentInfo computeNetPay(PaymentInfo paymentInfo) {
        BigDecimal netPay = paymentInfo.getGrossPay().get("Gross Pay").subtract(paymentInfo.getDeduction().get("total deduction"));
        paymentInfo.setNetPay(netPay);

        BigDecimal totalNetPay = sessionCalculationObject.getSummary().get("Total Net Pay");
        totalNetPay = totalNetPay.add(netPay);
        sessionCalculationObject.getSummary().put("Total Net Pay", totalNetPay);

        return paymentInfo;
    }

    private BigDecimal prorateBasicSalary(PaymentInfo paymentInfo){
        int numberOfDaysOfUnpaidAbsence = paymentInfo.getNumberOfDaysOfUnpaidAbsence();
        if (numberOfDaysOfUnpaidAbsence == 0)
            return paymentInfo.getBasicSalary();
        // if numberOfDaysOfUnpaidAbsence is not 0, remove the daily wage equivalent multiplied by the number of unpaid absences
        BigDecimal dailyWage = paymentInfo.getBasicSalary().divide(BigDecimal.valueOf(21), tRounding);  // To do ==> verify number of working days in the month
         return paymentInfo.getBasicSalary().subtract(dailyWage.multiply(BigDecimal.valueOf(numberOfDaysOfUnpaidAbsence)), tRounding);
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
        return total;
    }
}

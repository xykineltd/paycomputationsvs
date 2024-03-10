package com.xykine.computation.service;

import com.xykine.computation.entity.AllowanceAndOtherPayments;
import com.xykine.computation.entity.Deductions;
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


@Service
@RequiredArgsConstructor
public class PaymentCalculatorImpl implements PaymentCalculator{

    private final AllowanceAndOtherPaymentsRepo allowanceAndOtherPaymentsRepo;
    private final TaxRepo taxRepo;
    private final DeductionRepo deductionRepo;
    private final PensionFundRepo pensionFundRepo;
    private final SessionCalculationObject sessionCalculationObject;
    private final MathContext tRounding = new MathContext(2, RoundingMode.HALF_EVEN);

    // To do ==> complete implementation
    @Override
    public PaymentInfo computeTotalEarning(PaymentInfo paymentInfo) {
        String band = paymentInfo.getBandCode();
        Map<String, BigDecimal> totalEarningMap = new HashMap<>();
        totalEarningMap.put(Earnings.BASIC_SALARY.getGroup(), prorateBasicSalary(paymentInfo));
        totalEarningMap.put(Earnings.HOURLY_PAID.getGroup(), BigDecimal.ZERO);
        totalEarningMap.put(Earnings.ADDITIONAL_PAYMENT.getGroup(), calculateAdditionalPayment(paymentInfo));
        totalEarningMap.put(Earnings.OVERTIME.getGroup(), calculateOvertime(paymentInfo));
        totalEarningMap = insertRecurrentPaymentMap(totalEarningMap, band);
        BigDecimal total = getTotal(totalEarningMap);
        totalEarningMap.put("total earning", total);
        paymentInfo.setEarning(totalEarningMap);
        sessionCalculationObject.setTotalAmount(sessionCalculationObject.getTotalAmount().add(total));
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeTotalDeduction(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> totalDeductionMap = new HashMap<>();
        totalDeductionMap.put("Income Tax", calculateTax(paymentInfo));
        totalDeductionMap.put("Pension Fund",calculatePensionFund(paymentInfo));
        List<Deductions> deductions = deductionRepo.findDeductionByEmployeeId(paymentInfo.getId());
        List<AllowanceAndOtherPayments> allowanceAndOtherPayments = allowanceAndOtherPaymentsRepo.findByBandCode(paymentInfo.getBandCode());

        deductions.stream()
                .filter(x -> x.getActive())
                        .forEach(x -> {
                            totalDeductionMap.put(x.getDescription(), x.getAmount());
                        });

        allowanceAndOtherPayments.stream()
                        .filter(x -> x.getActive() && x.getTaxBearer().compareTo(TaxBearer.EMPLOYEE) == 0)
                                .forEach(x -> {
                                    totalDeductionMap.put("tax amount on " + x.getDescription(), taxDeductionOnAllowance(x));
                                });

        allowanceAndOtherPayments.stream()
                .filter(x -> x.getActive() && x.getTaxBearer().compareTo(TaxBearer.EMPLOYER) == 0)
                .forEach(x -> {
                    BigDecimal amountDue = taxDeductionOnAllowance(x);
                    sessionCalculationObject.getEmployerBornTaxDetails().put("Employer tax amount on " + x.getDescription()  + " for " + paymentInfo.getFullName(), amountDue);
                    sessionCalculationObject.setTotalAmount(sessionCalculationObject.getTotalAmount().add(amountDue));
                });

        totalDeductionMap.put("total deduction", getTotal(totalDeductionMap));
        paymentInfo.setDeduction(totalDeductionMap);
        return paymentInfo;
    }

    private Map<String, BigDecimal> insertRecurrentPaymentMap(Map<String, BigDecimal> earningMap, String band){
        List<AllowanceAndOtherPayments> allowanceAndOtherPayments = allowanceAndOtherPaymentsRepo.findByBandCode(band);
        allowanceAndOtherPayments.stream()
                .filter(x -> x.getActive())
                .forEach(x -> {
                    earningMap.put(x.getDescription(), applyTaxOnAllowance(x));
                });
        return earningMap;
    }

    private BigDecimal applyTaxOnAllowance(AllowanceAndOtherPayments allowanceAndOtherPayments){

        if (allowanceAndOtherPayments.getTaxPercent().compareTo(BigDecimal.ZERO) == 0 || allowanceAndOtherPayments.getTaxBearer().compareTo(TaxBearer.EMPLOYER) == 0) {
            return allowanceAndOtherPayments.getAmount();
        } else {
            return  allowanceAndOtherPayments.getAmount().multiply(BigDecimal.valueOf(100).subtract(allowanceAndOtherPayments.getTaxPercent())).divide(BigDecimal.valueOf(100), tRounding);
        }
    }

    private BigDecimal taxDeductionOnAllowance(AllowanceAndOtherPayments allowanceAndOtherPayments){
        return allowanceAndOtherPayments.getAmount().multiply(allowanceAndOtherPayments.getTaxPercent()).divide(BigDecimal.valueOf(100), tRounding);
    }

    @Override
    public PaymentInfo computeAmountDue(PaymentInfo paymentInfo) {
        paymentInfo.setTotalAmountDue(paymentInfo.getEarning().get("total earning").subtract(paymentInfo.getDeduction().get("total deduction")));
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

    private BigDecimal calculateTax(PaymentInfo paymentInfo){
        BigDecimal taxPercentage = taxRepo.findTaxByBand(paymentInfo.getBandCode()).getPercentage();
        BigDecimal taxAmount = prorateBasicSalary(paymentInfo).multiply(taxPercentage, tRounding).divide(BigDecimal.valueOf(100));
        return taxAmount;
    }

    private BigDecimal calculatePensionFund(PaymentInfo paymentInfo){
        //String taxClass = paymentInfo.getTaxClass(); to be set from admin service
        BigDecimal pensionPercentage = pensionFundRepo.findPensionFundByEmployeeId(paymentInfo.getId()).getPercentage();
        BigDecimal taxAmount = prorateBasicSalary(paymentInfo).multiply(pensionPercentage, tRounding).divide(BigDecimal.valueOf(100));
        return taxAmount;
    }


    // To do ==> complete implementation
    private BigDecimal calculateAdditionalPayment(PaymentInfo paymentInfo){
        return BigDecimal.ZERO;
    }

    // To do ==> complete implementation
    private BigDecimal calculateOvertime(PaymentInfo paymentInfo){
        return BigDecimal.ZERO;
    }



    private BigDecimal getTotal(Map<String, BigDecimal> input){
        BigDecimal total = BigDecimal.ZERO;

        for(Map.Entry<String, BigDecimal> entry : input.entrySet()) {
            total = total.add(entry.getValue());
        }
        return total;
    }
}

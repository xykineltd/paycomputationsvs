package com.xykine.computation.service;

import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.model.wagetypegroup.Deductions;
import com.xykine.computation.model.wagetypegroup.Earnings;
import com.xykine.computation.model.wagetypegroup.Others;
import com.xykine.computation.repo.T511KRepo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class PaymentCalculatorImpl implements PaymentCalculator{

    public final T511KRepo t511KRepo;

    // To do ==> complete implementation
    @Override
    public PaymentInfo computeTotalEarning(PaymentInfo paymentInfo) {

        Map<Enum, BigDecimal> totalEarningMap = new HashMap<>();
        totalEarningMap.put(Earnings.BASIC_SALARY, prorateBasicSalary(paymentInfo));
        totalEarningMap.put(Earnings.HOURLY_PAID, BigDecimal.ZERO);
        totalEarningMap.put(Earnings.ADDITIONAL_PAYMENT, calculateAdditionalPayment(paymentInfo));
        totalEarningMap.put(Earnings.RECURRING_PAYMENT, calculateRecurringPayment(paymentInfo));
        totalEarningMap.put(Earnings.OVERTIME, calculateRecurringPayment(paymentInfo));

        paymentInfo.setTotalEarning(getTotal(totalEarningMap));
        return paymentInfo;
    }


    // To do ==> complete implementation
    @Override
    public PaymentInfo computeTotalDeduction(PaymentInfo paymentInfo) {
        Map<Enum, BigDecimal> totalDeductionMap = new HashMap<>();
        totalDeductionMap.put(Deductions.ADDITIONAL_DEDUCTIONS, BigDecimal.ZERO);
        totalDeductionMap.put(Deductions.RECURRING_DEDUCTIONS, BigDecimal.ZERO);
        totalDeductionMap.put(Deductions.MEMBERSHIP_FEES, BigDecimal.ZERO);
        totalDeductionMap.put(Deductions.EXTERNAL_TRANSFER, BigDecimal.ZERO);

        paymentInfo.setTotalDeduction(getTotal(totalDeductionMap));
        return paymentInfo;
    }

    // To do ==> complete implementation
    @Override
    public PaymentInfo computeOthers(PaymentInfo paymentInfo) {
        Map<Enum, BigDecimal> otherPaymentMap = new HashMap<>();
        otherPaymentMap.put(Others.BASE_APPLICABLE_AMOUNT, BigDecimal.ZERO);
        otherPaymentMap.put(Others.BASE_STAT_ADDITIONAL, BigDecimal.ZERO);
        otherPaymentMap.put(Others.BASE_STAT_BASE_AMT, BigDecimal.ZERO);
        otherPaymentMap.put(Others.BASE_STAT_RECURRING, BigDecimal.ZERO);
        otherPaymentMap.put(Others.BASE_STAT_BASIC_PAY, BigDecimal.ZERO);
        otherPaymentMap.put(Others.EMPLOYER_CONTRIBUTION, BigDecimal.ZERO);
        otherPaymentMap.put(Others.LOANS, BigDecimal.ZERO);
        otherPaymentMap.put(Others.RESERVED, BigDecimal.ZERO);
        otherPaymentMap.put(Others.TRAVEL_MANAGEMENT, BigDecimal.ZERO);
        otherPaymentMap.put(Others.UPFRONT_AMORTIZES, BigDecimal.ZERO);
        otherPaymentMap.put(Others.UPFRONT_PAID_YTP, BigDecimal.ZERO);
        otherPaymentMap.put(Others.UPFRONT_WRITE_OFF, BigDecimal.ZERO);
        otherPaymentMap.put(Others.UPFRONT_BALANCES, BigDecimal.ZERO);
        otherPaymentMap.put(Others.TAX_CALCULATION, BigDecimal.ZERO);
        otherPaymentMap.put(Others.PROVISIONS, BigDecimal.ZERO);
        otherPaymentMap.put(Others.LEAVE_VALUATION_ABSENCES, BigDecimal.ZERO);

        paymentInfo.setOthers(getTotal(otherPaymentMap));
        return paymentInfo;
    }

    private BigDecimal prorateBasicSalary(PaymentInfo paymentInfo){
        int numberOfDaysOfUnpaidAbsence = paymentInfo.getNumberOfDaysOfUnpaidAbsence();

        if (numberOfDaysOfUnpaidAbsence == 0)
            return paymentInfo.getBasicSalary();

        // if numberOfDaysOfUnpaidAbsence is not 0, remove the daily wage equivalent multiplied by the number of unpaid absences
        BigDecimal dailyWage = paymentInfo.getBasicSalary().divide(BigDecimal.valueOf(21));  // To do ==> verify number of working days in the month
         return paymentInfo.getBasicSalary().subtract(dailyWage.multiply(BigDecimal.valueOf(numberOfDaysOfUnpaidAbsence)));
    }

    // To do ==> complete implementation
    private BigDecimal calculateAdditionalPayment(PaymentInfo paymentInfo){
        return BigDecimal.ZERO;
    }

    // To do ==> complete implementation
    private BigDecimal calculateRecurringPayment(PaymentInfo paymentInfo){
        return BigDecimal.ZERO;
    }

    // To do ==> complete implementation
    private BigDecimal calculateOvertime(PaymentInfo paymentInfo){
        return BigDecimal.ZERO;
    }

    private BigDecimal getTotal(Map<Enum, BigDecimal> input){
        BigDecimal total = BigDecimal.ZERO;

        for(Map.Entry<Enum, BigDecimal> entry : input.entrySet()) {
            total.add(entry.getValue());
        }
        return total;
    }
}

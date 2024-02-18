package com.xykine.computation.service;

import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.model.wagetypegroup.Deductions;
import com.xykine.computation.model.wagetypegroup.Earnings;
import com.xykine.computation.model.wagetypegroup.Others;
import com.xykine.computation.model.wagetypegroup.RecurringPayments;
import com.xykine.computation.repo.T511KRepo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class PaymentCalculatorImpl implements PaymentCalculator{

    public final T511KRepo t511KRepo;

    // To do ==> complete implementation
    @Override
    public PaymentInfo computeTotalEarning(PaymentInfo paymentInfo) {

        Map<String, BigDecimal> totalEarningMap = new HashMap<>();
        totalEarningMap.put(Earnings.BASIC_SALARY.getGroup(), prorateBasicSalary(paymentInfo));
        totalEarningMap.put(Earnings.HOURLY_PAID.getGroup(), BigDecimal.ZERO);
        totalEarningMap.put(Earnings.ADDITIONAL_PAYMENT.getGroup(), calculateAdditionalPayment(paymentInfo));
        totalEarningMap.put(Earnings.RECURRING_PAYMENT.getGroup(), calculateRecurringPayment(paymentInfo));
        totalEarningMap.put(Earnings.OVERTIME.getGroup(), calculateOvertime(paymentInfo));

        paymentInfo.setTotalEarning(getTotal(totalEarningMap));
        return paymentInfo;
    }


    // To do ==> complete implementation
    @Override
    public PaymentInfo computeTotalDeduction(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> totalDeductionMap = new HashMap<>();
        totalDeductionMap.put(Deductions.ADDITIONAL_DEDUCTIONS.getGroup(), BigDecimal.ZERO);
        totalDeductionMap.put(Deductions.RECURRING_DEDUCTIONS.getGroup(), BigDecimal.ZERO);
        totalDeductionMap.put(Deductions.MEMBERSHIP_FEES.getGroup(), BigDecimal.ZERO);
        totalDeductionMap.put(Deductions.EXTERNAL_TRANSFER.getGroup(), BigDecimal.ZERO);

        paymentInfo.setTotalDeduction(getTotal(totalDeductionMap));
        return paymentInfo;
    }

    // To do ==> complete implementation
    @Override
    public PaymentInfo computeOthers(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> otherPaymentMap = new HashMap<>();
        otherPaymentMap.put(Others.BASE_APPLICABLE_AMOUNT.getGroup(), BigDecimal.ZERO);
        otherPaymentMap.put(Others.BASE_STAT_ADDITIONAL.getGroup(), BigDecimal.ZERO);
        otherPaymentMap.put(Others.BASE_STAT_BASE_AMT.getGroup(), BigDecimal.ZERO);
        otherPaymentMap.put(Others.BASE_STAT_RECURRING.getGroup(), BigDecimal.ZERO);
        otherPaymentMap.put(Others.BASE_STAT_BASIC_PAY.getGroup(), BigDecimal.ZERO);
        otherPaymentMap.put(Others.EMPLOYER_CONTRIBUTION.getGroup(), BigDecimal.ZERO);
        otherPaymentMap.put(Others.LOANS.getGroup(), BigDecimal.ZERO);
        otherPaymentMap.put(Others.RESERVED.getGroup(), BigDecimal.ZERO);
        otherPaymentMap.put(Others.TRAVEL_MANAGEMENT.getGroup(), BigDecimal.ZERO);
        otherPaymentMap.put(Others.UPFRONT_AMORTIZES.getGroup(), BigDecimal.ZERO);
        otherPaymentMap.put(Others.UPFRONT_PAID_YTP.getGroup(), BigDecimal.ZERO);
        otherPaymentMap.put(Others.UPFRONT_WRITE_OFF.getGroup(), BigDecimal.ZERO);
        otherPaymentMap.put(Others.UPFRONT_BALANCES.getGroup(), BigDecimal.ZERO);
        otherPaymentMap.put(Others.TAX_CALCULATION.getGroup(), BigDecimal.ZERO);
        otherPaymentMap.put(Others.PROVISIONS.getGroup(), BigDecimal.ZERO);
        otherPaymentMap.put(Others.LEAVE_VALUATION_ABSENCES.getGroup(), BigDecimal.ZERO);

        paymentInfo.setOthers(getTotal(otherPaymentMap));
        return paymentInfo;
    }

    private BigDecimal prorateBasicSalary(PaymentInfo paymentInfo){
        int numberOfDaysOfUnpaidAbsence = paymentInfo.getNumberOfDaysOfUnpaidAbsence();
        MathContext tRounding = new MathContext(2, RoundingMode.HALF_EVEN)

        if (numberOfDaysOfUnpaidAbsence == 0)
            return paymentInfo.getBasicSalary();

        // if numberOfDaysOfUnpaidAbsence is not 0, remove the daily wage equivalent multiplied by the number of unpaid absences
        BigDecimal dailyWage = paymentInfo.getBasicSalary().divide(BigDecimal.valueOf(21), tRounding);  // To do ==> verify number of working days in the month
         return paymentInfo.getBasicSalary().subtract(dailyWage.multiply(BigDecimal.valueOf(numberOfDaysOfUnpaidAbsence)), tRounding);
    }

    // To do ==> complete implementation
    private BigDecimal calculateAdditionalPayment(PaymentInfo paymentInfo){
        return BigDecimal.ZERO;
    }


    // To do ==> complete implementation
    //To do ===> prorate values retrieved from T511K where required
    private BigDecimal calculateRecurringPayment(PaymentInfo paymentInfo){
        String constant;
        //  To resolve the constant from employee band;
        Map<String, BigDecimal> recurrinPaymentMap = new HashMap<>();
        recurrinPaymentMap.put(RecurringPayments.TRANSPORT_ALLOWANCE.getDescription(), t511KRepo.findAmountByConstant("ZFUC"));  // To do resolve employee transport allowance constant
        recurrinPaymentMap.put(RecurringPayments.STEWARD_ALLOWANCE.getDescription(), t511KRepo.findAmountByConstant("ZFUC"));
        recurrinPaymentMap.put(RecurringPayments.SECURITY_ALLOWANCE.getDescription(), t511KRepo.findAmountByConstant("ZFUC"));
        recurrinPaymentMap.put(RecurringPayments.LUNCH_ALLOWANCE.getDescription(), t511KRepo.findAmountByConstant("ZFUC"));
        recurrinPaymentMap.put(RecurringPayments.CAR_MAINTENANCE_ALLOWANCE.getDescription(), t511KRepo.findAmountByConstant("ZFUC"));
        recurrinPaymentMap.put(RecurringPayments.DATA_ALLOWANCE.getDescription(), t511KRepo.findAmountByConstant("ZFUC"));
        recurrinPaymentMap.put(RecurringPayments.FUEL_ALLOWANCE.getDescription(), t511KRepo.findAmountByConstant("ZFUC"));

        return getTotal(recurrinPaymentMap);
    }

    // To do ==> complete implementation
    private BigDecimal calculateOvertime(PaymentInfo paymentInfo){
        return BigDecimal.ZERO;
    }

    private BigDecimal getTotal(Map<String, BigDecimal> input){
        BigDecimal total = BigDecimal.ZERO;

        for(Map.Entry<String, BigDecimal> entry : input.entrySet()) {
            total.add(entry.getValue());
        }
        return total;
    }
}

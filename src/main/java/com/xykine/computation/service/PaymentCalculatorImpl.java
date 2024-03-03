package com.xykine.computation.service;

import com.xykine.computation.entity.Deductions;
import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.model.wagetypegroup.Earnings;
import com.xykine.computation.model.wagetypegroup.Others;
import com.xykine.computation.model.wagetypegroup.RecurringPayments;
import com.xykine.computation.repo.DeductionRepo;
import com.xykine.computation.repo.PensionFundRepo;
import com.xykine.computation.repo.T511KRepo;

import com.xykine.computation.repo.TaxRepo;
import com.xykine.computation.response.PaymentResponse;
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

    private final T511KRepo t511KRepo;
    private final TaxRepo taxRepo;
    private final DeductionRepo deductionRepo;
    private final PensionFundRepo pensionFundRepo;

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
        totalEarningMap.put("total earning", getTotal(totalEarningMap));
        paymentInfo.setEarning(totalEarningMap);
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeTotalDeduction(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> totalDeductionMap = new HashMap<>();
        totalDeductionMap.put("Income Tax", calculateTax(paymentInfo));
        totalDeductionMap.put("Pension Fund",calculatePensionFund(paymentInfo));
        List<Deductions> deductions = deductionRepo.findDeductionByEmployeeId(paymentInfo.getId());
        deductions.stream()
                .filter(x -> x.getActive())
                        .forEach(x -> {
                            totalDeductionMap.put(x.getDescription(), x.getAmount());
                        });
        totalDeductionMap.put("total deduction", getTotal(totalDeductionMap));
        paymentInfo.setDeduction(totalDeductionMap);
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
        otherPaymentMap.put("total others", getTotal(otherPaymentMap));
        paymentInfo.setOthers(otherPaymentMap);
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeAmountDue(PaymentInfo paymentInfo) {
        paymentInfo.setTotalAmountDue(paymentInfo.getEarning().get("total earning").subtract(paymentInfo.getDeduction().get("total deduction")));
        return paymentInfo;
    }

    private BigDecimal prorateBasicSalary(PaymentInfo paymentInfo){
        int numberOfDaysOfUnpaidAbsence = paymentInfo.getNumberOfDaysOfUnpaidAbsence();
        MathContext tRounding = new MathContext(2, RoundingMode.HALF_EVEN);
        if (numberOfDaysOfUnpaidAbsence == 0)
            return paymentInfo.getBasicSalary();
        // if numberOfDaysOfUnpaidAbsence is not 0, remove the daily wage equivalent multiplied by the number of unpaid absences
        BigDecimal dailyWage = paymentInfo.getBasicSalary().divide(BigDecimal.valueOf(21), tRounding);  // To do ==> verify number of working days in the month
         return paymentInfo.getBasicSalary().subtract(dailyWage.multiply(BigDecimal.valueOf(numberOfDaysOfUnpaidAbsence)), tRounding);
    }

    private BigDecimal calculateTax(PaymentInfo paymentInfo){
        BigDecimal taxPercentage = taxRepo.findTaxByBand(paymentInfo.getBandCode()).getPercentage();
        MathContext tRounding = new MathContext(2, RoundingMode.HALF_EVEN);
        BigDecimal taxAmount = prorateBasicSalary(paymentInfo).multiply(taxPercentage, tRounding).divide(BigDecimal.valueOf(100));
        return taxAmount;
    }

    private BigDecimal calculatePensionFund(PaymentInfo paymentInfo){
        //String taxClass = paymentInfo.getTaxClass(); to be set from admin service
        BigDecimal pensionPercentage = pensionFundRepo.findPensionFundByEmployeeId(paymentInfo.getId()).getPercentage();
        MathContext tRounding = new MathContext(2, RoundingMode.HALF_EVEN);
        BigDecimal taxAmount = prorateBasicSalary(paymentInfo).multiply(pensionPercentage, tRounding).divide(BigDecimal.valueOf(100));
        return taxAmount;
    }


    // To do ==> complete implementation
    private BigDecimal calculateAdditionalPayment(PaymentInfo paymentInfo){
        return BigDecimal.ZERO;
    }


    // To do ==> complete implementation
    //To do ===> prorate values retrieved from T511K where required
    private Map<String, BigDecimal> insertRecurrentPaymentMap(Map<String, BigDecimal> earningMap, String band){
        //  To resolve the constant from employee band;
        earningMap.put(RecurringPayments.TRANSPORT_ALLOWANCE.getDescription(), t511KRepo.findRecordByConstant("ZTR"+ band).getAmount());  // To do resolve employee transport allowance constant
        earningMap.put(RecurringPayments.STEWARD_ALLOWANCE.getDescription(), t511KRepo.findRecordByConstant("ZST"+ band).getAmount());
        earningMap.put(RecurringPayments.SECURITY_ALLOWANCE.getDescription(), t511KRepo.findRecordByConstant("ZSE"+ band).getAmount());
        earningMap.put(RecurringPayments.LUNCH_ALLOWANCE.getDescription(), t511KRepo.findRecordByConstant("Z1022").getAmount());
        earningMap.put(RecurringPayments.CAR_MAINTENANCE_ALLOWANCE.getDescription(), t511KRepo.findRecordByConstant("ZCA"+ band).getAmount());
        earningMap.put(RecurringPayments.DATA_ALLOWANCE.getDescription(), t511KRepo.findRecordByConstant("Z1021").getAmount());
        earningMap.put(RecurringPayments.FUEL_ALLOWANCE.getDescription(), t511KRepo.findRecordByConstant("ZFU"+ band).getAmount());
        earningMap.put(RecurringPayments.DRIVER_ALLOWANCE.getDescription(), t511KRepo.findRecordByConstant("ZDR"+band).getAmount());
        return earningMap;
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

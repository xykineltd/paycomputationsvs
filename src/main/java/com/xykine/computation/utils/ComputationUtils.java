package com.xykine.computation.utils;

import com.xykine.computation.response.SummaryDetail;
import com.xykine.computation.service.PaymentCalculatorImpl;
import com.xykine.computation.session.SessionCalculationObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xykine.payroll.model.PaymentFrequencyEnum;
import org.xykine.payroll.model.PaymentInfo;
import org.xykine.payroll.model.PaymentSettingsResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;


public class ComputationUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCalculatorImpl.class);

    public static BigDecimal prorate(BigDecimal rawValue, int numberOfUnPaiAbsence, PaymentFrequencyEnum salaryFrequency){

        if (rawValue == null)
            return BigDecimal.ZERO;

        if (salaryFrequency == null)
            return rawValue.divide(BigDecimal.valueOf(1), 2,  RoundingMode.CEILING);

        if (salaryFrequency.compareTo(PaymentFrequencyEnum.MONTHLY) == 0)
            rawValue = rawValue.divide(BigDecimal.valueOf(12), 2,  RoundingMode.CEILING);

        if (salaryFrequency.compareTo(PaymentFrequencyEnum.WEEKLY) == 0)
            rawValue = rawValue.divide(BigDecimal.valueOf(4), 2,  RoundingMode.CEILING);

        if (salaryFrequency.compareTo(PaymentFrequencyEnum.BI_WEEKLY) == 0)
            rawValue = rawValue.divide(BigDecimal.valueOf(2), 2,  RoundingMode.CEILING);

        if (numberOfUnPaiAbsence == 0)
            return rawValue;
//      if numberOfDaysOfUnpaidAbsence is not 0, remove the daily wage equivalent multiplied by the number of unpaid absences
        BigDecimal dailyEquivalent = rawValue.divide(BigDecimal.valueOf(21), 2,  RoundingMode.CEILING); // To do ==> verify number of working days in the month
        return roundToTwoDecimalPlaces(rawValue.subtract(dailyEquivalent.multiply(BigDecimal.valueOf(numberOfUnPaiAbsence))));
    }

    public static BigDecimal roundToTwoDecimalPlaces(BigDecimal input){
        return input.setScale(2, RoundingMode.CEILING);
    }

    public static synchronized void updateReportSummary(PaymentInfo paymentInfo, SessionCalculationObject sessionCalculationObject, String key, BigDecimal value){
        BigDecimal currentValue = sessionCalculationObject.getSummary().get(key);
        value = value != null ? value : BigDecimal.ZERO;
        currentValue = currentValue.add(value);
        sessionCalculationObject.getSummary().put(key, currentValue);

        List<SummaryDetail> summaryDetailsList = sessionCalculationObject.getSummaryDetails().get(key);
        SummaryDetail summaryDetail = SummaryDetail.builder()
                .employeeName(paymentInfo.getFullName())
                .departmentName(paymentInfo.getDepartmentName())
                .value(value)
                .build();
        summaryDetailsList.add(summaryDetail);
        sessionCalculationObject.getSummaryDetails().put(key, summaryDetailsList);
    }

    public static BigDecimal getPaymentValueFromPaymentSetting(PaymentSettingsResponse paymentSettings){
        var paymentSettingValue = paymentSettings.getValue() == null ? BigDecimal.valueOf(0.0) : paymentSettings.getValue();
        return paymentSettingValue;
    }

    public static BigDecimal getPaymentValueFromBaseSalary(BigDecimal paymentValue){
        if(paymentValue == null) {
            return BigDecimal.valueOf(0.0);
        }
        return paymentValue;
    }

    public static BigDecimal getTaxAmount(BigDecimal aTaxableIncome, SessionCalculationObject sessionCalculationObject){
        if(aTaxableIncome.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        aTaxableIncome = aTaxableIncome.multiply(BigDecimal.valueOf(12));
//      7% on the first 300000
        BigDecimal taxableIncome = aTaxableIncome.subtract(BigDecimal.valueOf(300000));
        BigDecimal taxAmount = BigDecimal.ZERO;
        if (taxableIncome.compareTo(BigDecimal.ZERO) == -1) {
            taxAmount = sessionCalculationObject.getComputationConstants().get("TaxClassA").multiply(aTaxableIncome).divide(BigDecimal.valueOf(100));
            return taxAmount.divide(BigDecimal.valueOf(12), RoundingMode.CEILING).setScale(2, RoundingMode.CEILING);
        } else {
            taxAmount = sessionCalculationObject.getComputationConstants().get("TaxClassA").multiply(getRemnant(taxableIncome, 300000)).divide(BigDecimal.valueOf(100));
        }

//      11% on the next 3000000
        taxAmount = taxAmount.add(sessionCalculationObject.getComputationConstants().get("TaxClassB").multiply(getRemnant(taxableIncome, 300000)).divide(BigDecimal.valueOf(100)));
        taxableIncome = taxableIncome.subtract(BigDecimal.valueOf(300000));
        if (taxableIncome.compareTo(BigDecimal.ZERO) == -1)
            return taxAmount.divide(BigDecimal.valueOf(12), RoundingMode.CEILING).setScale(2, RoundingMode.CEILING);;

//      15% on the next 5000000
        taxAmount = taxAmount.add(sessionCalculationObject.getComputationConstants().get("TaxClassC").multiply(getRemnant(taxableIncome, 500000)).divide(BigDecimal.valueOf(100)));
        taxableIncome = taxableIncome.subtract(BigDecimal.valueOf(500000));
        if (taxableIncome.compareTo(BigDecimal.ZERO) == -1)
            return taxAmount.divide(BigDecimal.valueOf(12), RoundingMode.CEILING).setScale(2, RoundingMode.CEILING);;

//      19% on the next 3000000
        taxAmount = taxAmount.add(sessionCalculationObject.getComputationConstants().get("TaxClassD").multiply(getRemnant(taxableIncome, 500000)).divide(BigDecimal.valueOf(100)));
        taxableIncome = taxableIncome.subtract(BigDecimal.valueOf(500000));
        if (taxableIncome.compareTo(BigDecimal.ZERO) == -1)
            return taxAmount.divide(BigDecimal.valueOf(12), RoundingMode.CEILING).setScale(2, RoundingMode.CEILING);

//      21% on the next 1600000
        taxAmount = taxAmount.add(sessionCalculationObject.getComputationConstants().get("TaxClassE").multiply(getRemnant(taxableIncome, 1600000)).divide(BigDecimal.valueOf(100)));
        taxableIncome = taxableIncome.subtract(BigDecimal.valueOf(1600000));
        if (taxableIncome.compareTo(BigDecimal.ZERO) == -1)
            return taxAmount.divide(BigDecimal.valueOf(12), RoundingMode.CEILING).setScale(2, RoundingMode.CEILING);;

        //      24% on the remaining
        taxAmount = taxAmount.add(sessionCalculationObject.getComputationConstants().get("TaxClassF").multiply(taxableIncome).divide(BigDecimal.valueOf(100)));

        return taxAmount.divide(BigDecimal.valueOf(12), RoundingMode.CEILING).setScale(2, RoundingMode.CEILING);
    }

    public  static BigDecimal exchangeToLocalCurrency(BigDecimal exchangeRate, BigDecimal amount){
        if (amount == null)
            return BigDecimal.ZERO;
        return roundToTwoDecimalPlaces(exchangeRate.multiply(amount));
    }

    public static BigDecimal exchangeToForeignCurrency(BigDecimal exchangeRate, BigDecimal amount){
        return roundToTwoDecimalPlaces(amount.divide(exchangeRate));
    }

    public static BigDecimal harmoniseToAnnual(long multiplier, BigDecimal amount){

        return roundToTwoDecimalPlaces(BigDecimal.valueOf(multiplier).multiply(amount));
    }

    private static BigDecimal getRemnant(BigDecimal remnantTaxable, Integer nextLevel) {
        if (remnantTaxable.compareTo(BigDecimal.valueOf(nextLevel)) == -1)
            return remnantTaxable;
        return BigDecimal.valueOf(nextLevel);
    }
}

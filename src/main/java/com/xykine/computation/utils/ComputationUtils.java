package com.xykine.computation.utils;


import com.xykine.computation.service.PaymentCalculatorImpl;
import com.xykine.computation.session.SessionCalculationObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ComputationUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCalculatorImpl.class);
    public static BigDecimal prorate(BigDecimal rawValue, int numberOfUnPaiAbsence){
        rawValue = rawValue.divide(BigDecimal.valueOf(12), 2,  RoundingMode.CEILING);
        if (numberOfUnPaiAbsence == 0)
            return rawValue;
        // if numberOfDaysOfUnpaidAbsence is not 0, remove the daily wage equivalent multiplied by the number of unpaid absences
        BigDecimal dailyEquivalent = rawValue.divide(BigDecimal.valueOf(21), 2,  RoundingMode.CEILING); // To do ==> verify number of working days in the month
        return roundToTwoDecimalPlaces(rawValue.subtract(dailyEquivalent.multiply(BigDecimal.valueOf(numberOfUnPaiAbsence))));
    }

    public static BigDecimal roundToTwoDecimalPlaces(BigDecimal input){
        return input.setScale(2, RoundingMode.CEILING);
    }

    public static BigDecimal getTaxAmount(BigDecimal taxableIncome, SessionCalculationObject sessionCalculationObject){
        BigDecimal taxAmount = sessionCalculationObject.getComputationConstants().get("TaxClassA").multiply(BigDecimal.valueOf(300000)).divide(BigDecimal.valueOf(100));
        taxableIncome = taxableIncome.multiply(BigDecimal.valueOf(12));

//      7% on the first 3000000
        taxableIncome = taxableIncome.subtract(BigDecimal.valueOf(300000));
        if (taxableIncome.compareTo(BigDecimal.ZERO) == -1)
            return taxAmount.divide(BigDecimal.valueOf(12), RoundingMode.CEILING).setScale(2, RoundingMode.CEILING);;

//      11% on the next 3000000
        taxAmount = taxAmount.add(sessionCalculationObject.getComputationConstants().get("TaxClassB").multiply(BigDecimal.valueOf(300000)).divide(BigDecimal.valueOf(100)));
        taxableIncome = taxableIncome.subtract(BigDecimal.valueOf(300000));
        if (taxableIncome.compareTo(BigDecimal.ZERO) == -1)
            return taxAmount.divide(BigDecimal.valueOf(12), RoundingMode.CEILING).setScale(2, RoundingMode.CEILING);;

//      15% on the next 5000000
        taxAmount = taxAmount.add(sessionCalculationObject.getComputationConstants().get("TaxClassC").multiply(BigDecimal.valueOf(500000)).divide(BigDecimal.valueOf(100)));
        taxableIncome = taxableIncome.subtract(BigDecimal.valueOf(500000));
        if (taxableIncome.compareTo(BigDecimal.ZERO) == -1)
            return taxAmount.divide(BigDecimal.valueOf(12), RoundingMode.CEILING).setScale(2, RoundingMode.CEILING);;

        //      19% on the next 3000000
        taxAmount = taxAmount.add(sessionCalculationObject.getComputationConstants().get("TaxClassD").multiply(BigDecimal.valueOf(500000)).divide(BigDecimal.valueOf(100)));
        taxableIncome = taxableIncome.subtract(BigDecimal.valueOf(500000));
        if (taxableIncome.compareTo(BigDecimal.ZERO) == -1)
            return taxAmount.divide(BigDecimal.valueOf(12), RoundingMode.CEILING).setScale(2, RoundingMode.CEILING);

        //      21% on the next 1600000
        taxAmount = taxAmount.add(sessionCalculationObject.getComputationConstants().get("TaxClassE").multiply(BigDecimal.valueOf(1600000)).divide(BigDecimal.valueOf(100)));
        taxableIncome = taxableIncome.subtract(BigDecimal.valueOf(1600000));
        if (taxableIncome.compareTo(BigDecimal.ZERO) == -1)
            return taxAmount.divide(BigDecimal.valueOf(12), RoundingMode.CEILING).setScale(2, RoundingMode.CEILING);;

        //      24% on the remaining
        taxAmount = taxAmount.add(sessionCalculationObject.getComputationConstants().get("TaxClassF").multiply(taxableIncome).divide(BigDecimal.valueOf(100)));

        return taxAmount.divide(BigDecimal.valueOf(12), RoundingMode.CEILING).setScale(2, RoundingMode.CEILING);
    }
}

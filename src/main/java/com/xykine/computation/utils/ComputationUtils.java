package com.xykine.computation.utils;


import com.xykine.computation.service.PaymentCalculatorImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ComputationUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCalculatorImpl.class);
    public static BigDecimal prorate(BigDecimal rawValue, int numberOfUnPaiAbsence){

        LOGGER.debug(" rawValue ==> {} ", rawValue);
        rawValue = rawValue.divide(BigDecimal.valueOf(12), 2,  RoundingMode.CEILING);
        LOGGER.debug(" prorated value ==> {} ", rawValue);
        if (numberOfUnPaiAbsence == 0)
            return rawValue;
        // if numberOfDaysOfUnpaidAbsence is not 0, remove the daily wage equivalent multiplied by the number of unpaid absences
        BigDecimal dailyEquivalent = rawValue.divide(BigDecimal.valueOf(21), 2,  RoundingMode.CEILING); // To do ==> verify number of working days in the month
        return roundToTwoDecimalPlaces(rawValue.subtract(dailyEquivalent.multiply(BigDecimal.valueOf(numberOfUnPaiAbsence))));
    }

    public static BigDecimal roundToTwoDecimalPlaces(BigDecimal input){
        return input.setScale(2, RoundingMode.CEILING);
    }
}

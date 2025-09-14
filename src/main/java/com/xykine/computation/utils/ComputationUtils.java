package com.xykine.computation.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykine.computation.entity.TaxRule;
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
import java.util.ArrayList;
import java.util.Collections;
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

    public static void updateReportSummary(PaymentInfo paymentInfo,
                                           SessionCalculationObject sessionCalculationObject,
                                           String key,
                                           BigDecimal value) {

        value = value != null ? value : BigDecimal.ZERO;

        // Atomic update of summary
        sessionCalculationObject.getSummary().merge(key, value, BigDecimal::add);

        // Thread-safe update of summaryDetails
        sessionCalculationObject.getSummaryDetails()
                .computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(SummaryDetail.builder()
                        .employeeName(paymentInfo.getFullName())
                        .departmentName(paymentInfo.getDepartmentName())
                        .value(value)
                        .build());
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

    public static BigDecimal getTaxAmount(BigDecimal taxableIncome, String taxRuleJson){
        if (taxableIncome.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        // Convert to annual
        taxableIncome = taxableIncome.multiply(BigDecimal.valueOf(12));
        List<TaxRule> rules = getTaxRuleList(taxRuleJson);
        BigDecimal taxAmount = BigDecimal.ZERO;
        for (TaxRule rule : rules) {
            if (taxableIncome.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal rate = BigDecimal.valueOf(rule.getRate());
            if (rule.getLimit() == null) {
                // Apply on remaining income
                taxAmount = taxAmount.add(rate.multiply(taxableIncome).divide(BigDecimal.valueOf(100)));
                taxableIncome = BigDecimal.ZERO;
            } else {
                BigDecimal applicableAmount = taxableIncome.min(BigDecimal.valueOf(rule.getLimit()));
                taxAmount = taxAmount.add(rate.multiply(applicableAmount).divide(BigDecimal.valueOf(100)));
                taxableIncome = taxableIncome.subtract(applicableAmount);
            }
        }
        // Convert back to monthly
        return taxAmount.divide(BigDecimal.valueOf(12), 2, RoundingMode.CEILING);
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

    public static List<TaxRule> getTaxRuleList(String taxRuleJsonString) {
        ObjectMapper mapper = new ObjectMapper();
        if (taxRuleJsonString == null || taxRuleJsonString.isBlank()) {
            LOGGER.warn("taxRule JSON string is null or empty.");
            return Collections.emptyList();
        }
        try {
            String innerTaxRuleJson = mapper.readTree(taxRuleJsonString)
                    .get("taxRule")
                    .asText();
            return mapper.readValue(innerTaxRuleJson, new TypeReference<List<TaxRule>>() {});
        } catch (Exception e) {
            LOGGER.error("Failed to parse taxRule JSON: {}", taxRuleJsonString, e);
            return Collections.emptyList();
        }
    }
}

package com.xykine.computation.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykine.computation.entity.Loan;
import com.xykine.computation.entity.PaymentDistribution;
import com.xykine.computation.entity.PaymentSettingMetaData;
import com.xykine.computation.entity.TaxRule;
import com.xykine.computation.response.SummaryDetail;
import com.xykine.computation.service.PaymentCalculatorImpl;
import com.xykine.computation.session.SessionCalculationObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xykine.payroll.model.PaymentFrequencyEnum;
import org.xykine.payroll.model.PaymentInfo;
import org.xykine.payroll.model.PaymentSettingsResponse;
import org.xykine.payroll.model.enums.CurrencyEnum;
import org.xykine.payroll.model.enums.PaymentTypeEnum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


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

        String employeeId = paymentInfo.getEmployeeID();
        String employeeCostCenter = "";
        Map<String, List<String>> costCenterDetails = sessionCalculationObject.getCostCenters();

        if (costCenterDetails != null && !costCenterDetails.isEmpty()) {
            for (Map.Entry<String, List<String>> entry : costCenterDetails.entrySet()) {
                if (entry.getValue().contains(employeeId)) {
                    employeeCostCenter = entry.getKey();
                    break;
                }
            }
            Map<String, ConcurrentHashMap<String, BigDecimal>> costCenterSummaryMap = sessionCalculationObject.getCostCenterSummary();
            ConcurrentHashMap<String, BigDecimal> costCenterSummary = costCenterSummaryMap.get(employeeCostCenter);
                costCenterSummaryMap.put(employeeCostCenter, costCenterSummary);
                sessionCalculationObject.setCostCenterSummary(costCenterSummaryMap);
        }

        // Thread-safe update of summaryDetails
        sessionCalculationObject.getSummaryDetails()
                .computeIfAbsent(key, k -> Collections.synchronizedSet(new HashSet<>()))
                .add(SummaryDetail.builder()
                        .employeeId(paymentInfo.getEmployeeID())
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

    public static BigDecimal getAnnualTaxAmount(BigDecimal taxableIncome, String taxRuleJson) {
        if (taxableIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
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
        // ✅ Return ANNUAL tax (same as method1)
        return taxAmount.setScale(2, RoundingMode.HALF_UP);
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

    public static List<PaymentDistribution> getPaymentDistribution(String paymentDistributionJson) {
        ObjectMapper mapper = new ObjectMapper();
        if (paymentDistributionJson == null || paymentDistributionJson.isBlank()) {
            LOGGER.warn("paymentDistributionJson JSON string is null or empty.");
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(paymentDistributionJson, new TypeReference<List<PaymentDistribution>>() {});
        } catch (Exception e) {
            LOGGER.error("Failed to parse paymentDistributionJson JSON: {}", paymentDistributionJson, e);
            return Collections.emptyList();
        }
    }

    public static Set<PaymentSettingsResponse> getExpandedPaymentDistribution(PaymentInfo paymentInfo,
            List<PaymentDistribution> distributions
    ) {
        Set<PaymentSettingsResponse> result = new HashSet<>();

        if (distributions.isEmpty()) {
            return result; // return empty list if null inputs
        }

        for (PaymentDistribution dist : distributions) {
            PaymentSettingsResponse copy = new PaymentSettingsResponse();

            copy.setEmployeeID(paymentInfo.getEmployeeID());
            copy.setCurrency(paymentInfo.getCurrency());
            copy.setSalaryFrequency(PaymentFrequencyEnum.YEARLY);
            copy.setActive(false);
            copy.setPensionable(false);
            copy.setProrated(false);

            // Replace the name with distribution name
            copy.setName(dist.getName());
            // Replace type with distribution type (convert String → PaymentTypeEnum if needed)
            if (dist.getType() != null) {
                copy.setType(PaymentTypeEnum.valueOf(dist.getType().toUpperCase()));
            } else {
                copy.setType(PaymentTypeEnum.ALLOWANCE_ANNUAL); // fallback
            }

            // Calculate distributed value (with rounding to 2 decimals)
            BigDecimal percentage = BigDecimal.valueOf(dist.getPercentage())
                    .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP); // high precision interim

            BigDecimal distributedValue = paymentInfo.getBasicSalary()
                    .multiply(percentage)
                    .setScale(2, RoundingMode.HALF_UP); // round to 2 decimals

            copy.setValue(distributedValue);
            result.add(copy);
        }
        return result;
    }

    public static Set<PaymentSettingsResponse> getEmployeeDeductions(List<Loan> loans) {
        Set<PaymentSettingsResponse> result = new HashSet<>();
        if (loans.isEmpty()) {
            return result;
        }
        loans.stream().forEach(x -> {
            PaymentSettingsResponse loan = new PaymentSettingsResponse();
            loan.setEmployeeID(x.getEmployeeId());
            loan.setCurrency(CurrencyEnum.NGN);
            loan.setSalaryFrequency(PaymentFrequencyEnum.MONTHLY);
            loan.setActive(true);
            loan.setValue(x.getScheduledRepaymentAmount());
            loan.setName(x.getDescription());
            loan.setType(PaymentTypeEnum.DEDUCTION_MONTHLY);
            result.add(loan);
        });
        return result;
    }

    public static boolean isValid(PaymentSettingsResponse response, List<PaymentSettingMetaData> settingsMetadata, LocalDate startDate) {
        if (doPrecheck(response, settingsMetadata)) {
           return true;
        }
        return settingsMetadata
                .stream()
                .filter(x -> x.getPaymentName().equalsIgnoreCase(response.getName()))
                .anyMatch(x -> !x.getStartDate().isAfter(startDate) && !x.getEndDate().isBefore(startDate));
    }

    public static boolean isProrated(PaymentSettingsResponse response, List<PaymentSettingMetaData> settingsMetadata) {
        if (doPrecheck(response, settingsMetadata)) {
            return true;
        }
        return !settingsMetadata
                .stream()
                .filter(x -> x.getPaymentName().equalsIgnoreCase(response.getName()) &&  x.getProrated())
                .findAny()
                .isEmpty();

    }

    public static boolean isTaxable(PaymentSettingsResponse response, List<PaymentSettingMetaData> settingsMetadata) {
        if (doPrecheck(response, settingsMetadata)) {
            return true;
        }
        return !settingsMetadata
                .stream()
                .filter(x -> x.getPaymentName().equalsIgnoreCase(response.getName()) &&  x.getTaxable())
                .findAny()
                .isEmpty();

    }

    private static boolean doPrecheck(PaymentSettingsResponse response, List<PaymentSettingMetaData> settingsMetadata){
        return settingsMetadata.stream().filter(x -> x.getPaymentName().equalsIgnoreCase(response.getName())).collect(Collectors.toSet()).isEmpty();
    }
}

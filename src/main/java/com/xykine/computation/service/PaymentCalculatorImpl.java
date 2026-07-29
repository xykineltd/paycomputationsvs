package com.xykine.computation.service;

import com.xykine.computation.domain.LoanStatus;
import com.xykine.computation.dto.LoanFilter;
import com.xykine.computation.entity.*;
import com.xykine.computation.exceptions.PayrollValidationException;
import com.xykine.computation.repo.PaymentSettingMetadataRepo;
import com.xykine.computation.repo.TaxRepo;
import com.xykine.computation.service.calculator.PensionNhfCalculator;
import com.xykine.computation.service.calculator.TaxReliefAndPayeEngine;
import com.xykine.computation.session.PayrollCalculationContext;
import com.xykine.computation.session.PayrollCalculationContextHolder;
import com.xykine.computation.session.PayrollSessionHolder;
import com.xykine.computation.utils.ComputationUtils;
import com.xykine.computation.utils.PayrollMapKeys;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.xykine.payroll.model.*;
import org.xykine.payroll.model.enums.PaymentTypeEnum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.xykine.computation.utils.ComputationUtils.*;

@Service
@RequiredArgsConstructor
public class PaymentCalculatorImpl implements PaymentCalculator {

    private final EmployeeMetadataService employeeMetadataService;
    private final CompanyMetadataService companyMetadataService;
    private final TaxRepo taxRepo;
    private final LoanService loanService;
    private final PaymentSettingMetadataRepo paymentSettingMetadataRepo;

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCalculatorImpl.class);
    private static final String DEFAULT_TAX_COUNTRY = "NIGERIA";

    @Override
    public PaymentInfo expandPaymentSettingsFromGrossAnnual(PaymentInfo paymentInfo) {
        String paymentDistributionJson = resolvePaymentDistributionJson(paymentInfo.getCompanyID());
        List<PaymentDistribution> paymentDistributionList =
                ComputationUtils.getPaymentDistribution(paymentDistributionJson);
        Set<PaymentSettingsResponse> fromDistribution =
                ComputationUtils.getExpandedPaymentDistribution(paymentInfo, paymentDistributionList);
        Set<PaymentSettingsResponse> originalSettings =
                paymentInfo.getPaymentSettings() != null
                        ? paymentInfo.getPaymentSettings()
                        : new HashSet<>();

        originalSettings.addAll(fromDistribution);
        if (originalSettings.isEmpty()) {
            throw new PayrollValidationException(
                    "No payment settings found for employeeId=" + paymentInfo.getEmployeeID()
                            + ". Configure employee payment settings or company payment distribution.");
        }
        paymentInfo.setPaymentSettings(originalSettings);
        return paymentInfo;
    }

    @Override
    public PaymentInfo applyExchange(PaymentInfo paymentInfo) {
        ExchangeInfo exchangeInfo = paymentInfo.getExchangeInfo();
        if (exchangeInfo == null || exchangeInfo.getExchangeRate() == null) {
            throw new PayrollValidationException(
                    "Exchange info/rate is required for employeeId=" + paymentInfo.getEmployeeID());
        }
        BigDecimal exchangeRate = exchangeInfo.getExchangeRate();
        if (exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PayrollValidationException(
                    "Exchange rate must be positive for employeeId=" + paymentInfo.getEmployeeID());
        }
        paymentInfo.setBasicSalary(
                ComputationUtils.exchangeToLocalCurrency(exchangeRate, paymentInfo.getBasicSalary()));
        Set<PaymentSettingsResponse> updated = new HashSet<>();
        paymentInfo.getPaymentSettings().stream()
                .filter(x -> x.getValue() != null)
                .forEach(x -> {
                    x.setValue(ComputationUtils.exchangeToLocalCurrency(exchangeRate, x.getValue()));
                    updated.add(x);
                });
        paymentInfo.setPaymentSettings(updated);
        return paymentInfo;
    }

    @Override
    public PaymentInfo addPersonalDeduction(PaymentInfo paymentInfo) {
        if (paymentInfo.isOffCycle()) {
            return paymentInfo;
        }
        List<Loan> employeeLoans = resolveLoans(paymentInfo);
        Set<PaymentSettingsResponse> loanDeductions = ComputationUtils.getEmployeeDeductions(employeeLoans);
        Set<PaymentSettingsResponse> settings =
                paymentInfo.getPaymentSettings() != null ? paymentInfo.getPaymentSettings() : new HashSet<>();
        settings.addAll(loanDeductions);
        paymentInfo.setPaymentSettings(settings);
        return paymentInfo;
    }

    @Override
    public PaymentInfo harmoniseToAnnual(PaymentInfo paymentInfo) {
        long multiplier = resolvePaymentEntryMultiplier(paymentInfo.getCompanyID());
        Set<PaymentSettingsResponse> updated = paymentInfo.getPaymentSettings().stream()
                .filter(x -> x.getValue() != null)
                .map(setting -> harmonisePaymentSetting(setting, multiplier))
                .collect(Collectors.toSet());
        paymentInfo.setPaymentSettings(updated);
        return paymentInfo;
    }

    private PaymentSettingsResponse harmonisePaymentSetting(PaymentSettingsResponse setting, long globalMultiplier) {
        String description = setting.getType().getDescription();
        if (description.contains("ALLOWANCE") || description.contains("BASIC SALARY")) {
            setting.setValue(ComputationUtils.harmoniseToAnnual(globalMultiplier, setting.getValue()));
            if (description.contains("HOUSING")) {
                setting.setType(PaymentTypeEnum.ALLOWANCE_ANNUAL_HOUSING);
            } else if (description.contains("TRANSPORT")) {
                setting.setType(PaymentTypeEnum.ALLOWANCE_ANNUAL_TRANSPORT);
            } else if (description.contains("BASIC SALARY")) {
                setting.setType(PaymentTypeEnum.BASIC_SALARY_ANNUAL);
            } else {
                setting.setType(PaymentTypeEnum.ALLOWANCE_ANNUAL);
            }
        } else if (description.contains("OFF CYCLE")) {
            long customMultiplier = getMultiplier(setting.getSalaryFrequency());
            setting.setValue(ComputationUtils.harmoniseToAnnual(customMultiplier, setting.getValue()));
            setting.setType(PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT);
            setting.setSalaryFrequency(PaymentFrequencyEnum.YEARLY);
        }
        return setting;
    }

    @Override
    public PaymentInfo computeGrossPay(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> grossPayMap = new HashMap<>();
        insertRecurrentPaymentMap(grossPayMap, paymentInfo);
        BigDecimal total = getTotal(grossPayMap);
        grossPayMap.put(MapKeys.GROSS_PAY, total);
        grossPayMap.put(PayrollMapKeys.GROSS_SALARY, paymentInfo.isOffCycle() ? BigDecimal.ZERO : total);
        paymentInfo.setGrossPay(grossPayMap);
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeNonTaxableIncomeExempt(PaymentInfo paymentInfo) {
        EmployeeMetadata meta = resolveEmployeeMetadata(paymentInfo);
        if (meta.getEmployeeType() == EmployeeType.CONTRACT) {
            return paymentInfo;
        }

        if (paymentInfo.isOffCycle()) {
            PaymentFrequencyEnum freq = getOffCyclePaymentFrequency(paymentInfo);
            return TaxReliefAndPayeEngine.applyOffCycleRelief(
                    paymentInfo, freq, resolvePaymentSettingsMeta(paymentInfo));
        }

        PaymentFrequencyEnum salaryFrequency = resolveSalaryFrequency(paymentInfo.getCompanyID());
        PensionNhfCalculator.Result pensionNhf =
                PensionNhfCalculator.compute(paymentInfo, meta, salaryFrequency);
        paymentInfo.setPension(pensionNhf.pension());
        paymentInfo.setNhf(pensionNhf.nhf());

        BigDecimal monthlyTaxFree = getTotalMonthlyTaxFreeAllowance(paymentInfo);
        TaxReliefAndPayeEngine.applyRegularRelief(
                paymentInfo,
                meta,
                pensionNhf.annualEmployeePension(),
                pensionNhf.annualNhf(),
                monthlyTaxFree);
        return paymentInfo;
    }

    /**
     * Absence proration is applied during {@link #computeGrossPay} via insertRecurrentPaymentMap.
     * Kept for interface compatibility; intentionally a no-op to avoid double-proration.
     */
    @Override
    public PaymentInfo prorateEarnings(PaymentInfo paymentInfo) {
        return paymentInfo;
    }

    @Override
    public PaymentInfo computePayeeTax(PaymentInfo paymentInfo) {
        EmployeeMetadata meta = resolveEmployeeMetadata(paymentInfo);
        if (meta.getEmployeeType() == EmployeeType.CONTRACT) {
            return paymentInfo;
        }
        Tax taxInfo = resolveTax();
        PaymentFrequencyEnum salaryFrequency = resolveSalaryFrequency(paymentInfo.getCompanyID());
        boolean offCycleTaxable = PayrollSessionHolder.get().isOffCycleTaxable();
        return TaxReliefAndPayeEngine.computePayeeTax(paymentInfo, taxInfo, salaryFrequency, offCycleTaxable);
    }

    @Override
    public PaymentInfo computeTotalDeduction(PaymentInfo paymentInfo) {
        Map<String, BigDecimal> deductionMap = new HashMap<>();
        EmployeeMetadata meta = resolveEmployeeMetadata(paymentInfo);

        if (meta.getEmployeeType() != EmployeeType.CONTRACT) {
            if (paymentInfo.isOffCycle()) {
                String payeeKey = resolveOffCyclePayeKey(paymentInfo);
                BigDecimal paye = paymentInfo.getPayeeTax() != null
                        ? paymentInfo.getPayeeTax().getOrDefault(payeeKey, BigDecimal.ZERO)
                        : BigDecimal.ZERO;
                deductionMap.put(payeeKey, paye);
                deductionMap.put(PayrollMapKeys.TOTAL_PAYE, paye);
                deductionMap.put(MapKeys.TOTAL_DEDUCTION, paye);
                updateReportSummary(paymentInfo, PayrollSessionHolder.get(),
                        MapKeys.TOTAL_PERSONAL_DEDUCTION, paye);
                paymentInfo.setDeduction(deductionMap);
                return paymentInfo;
            }

            BigDecimal paye = paymentInfo.getPayeeTax() != null
                    ? paymentInfo.getPayeeTax().getOrDefault(PayrollMapKeys.PAYE, BigDecimal.ZERO)
                    : BigDecimal.ZERO;
            deductionMap.put(PayrollMapKeys.PAYE, paye);
            deductionMap.put(PayrollMapKeys.TOTAL_PAYE, paye);
            deductionMap.put(MapKeys.PENSION_FUND,
                    paymentInfo.getPension().get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION));
            deductionMap.put(MapKeys.NATIONAL_HOUSING_FUND,
                    paymentInfo.getNhf().get(MapKeys.NATIONAL_HOUSING_FUND));

            getDeductionsForEmployee(paymentInfo).forEach(x -> {
                deductionMap.put(x.getName(), x.getValue());
                updateReportSummary(paymentInfo, PayrollSessionHolder.get(),
                        MapKeys.TOTAL_PERSONAL_DEDUCTION, x.getValue());
            });

            BigDecimal voluntary = nz(meta.getVoluntaryPensionContribution());
            deductionMap.put(PayrollMapKeys.VOLUNTARY_PENSION, voluntary);
            updateReportSummary(paymentInfo, PayrollSessionHolder.get(),
                    PayrollMapKeys.TOTAL_VOLUNTARY_PENSION, voluntary);

            deductionMap.put(MapKeys.TOTAL_DEDUCTION, getTotal(deductionMap));
            paymentInfo.setDeduction(deductionMap);
            updateReportSummary(paymentInfo, PayrollSessionHolder.get(),
                    MapKeys.TOTAL_EMPLOYEE_PENSION_CONTRIBUTION,
                    paymentInfo.getPension().get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION));
        } else {
            BigDecimal contractorGross = paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY);
            BigDecimal whtPercent = PayrollSessionHolder.get().getComputationConstants().get("withHoldingTax");
            BigDecimal whtAmount = ComputationUtils.roundToTwoDecimalPlaces(whtPercent.multiply(contractorGross));
            deductionMap.put(PayrollMapKeys.WHT, whtAmount);

            getDeductionsForEmployee(paymentInfo).forEach(x -> {
                deductionMap.put(x.getName(), x.getValue());
                updateReportSummary(paymentInfo, PayrollSessionHolder.get(),
                        MapKeys.TOTAL_PERSONAL_DEDUCTION, x.getValue());
            });

            deductionMap.put(MapKeys.TOTAL_DEDUCTION, getTotal(deductionMap));
            paymentInfo.setDeduction(deductionMap);

            Map<String, BigDecimal> taxRelief = new HashMap<>();
            taxRelief.put(PayrollMapKeys.MONTHLY_CHARGEABLE_INCOME, contractorGross);
            paymentInfo.setTaxRelief(taxRelief);

            updateReportSummary(paymentInfo, PayrollSessionHolder.get(),
                    PayrollMapKeys.WHT_DISPLAY, whtAmount);
        }
        return paymentInfo;
    }

    private Map<String, BigDecimal> insertRecurrentPaymentMap(
            Map<String, BigDecimal> earningMap, PaymentInfo paymentInfo) {
        PaymentFrequencyEnum salaryFrequency = paymentInfo.isOffCycle()
                ? getOffCyclePaymentFrequency(paymentInfo)
                : resolveSalaryFrequency(paymentInfo.getCompanyID());
        int unpaidDays = paymentInfo.getNumberOfDaysOfUnpaidAbsence();
        if (paymentInfo.isOffCycle()) {
            PaymentSettingsResponse offCycle = getOffCyclePaymentDetails(paymentInfo);
            if (offCycle.getName() != null) {
                earningMap.put(offCycle.getName(),
                        offCycle.getValue() != null ? offCycle.getValue() : BigDecimal.ZERO);
            }
        } else {
            // Do not mutate annual settings in place — pension/relief still need annual values.
            PensionNhfCalculator.getAllowanceForEmployee(paymentInfo).forEach(entry -> {
                int days = entry.getType() != PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT ? unpaidDays : 0;
                BigDecimal periodValue = prorate(
                        entry.getValue(), days, salaryFrequency, paymentInfo.getStartDate());
                earningMap.put(entry.getName(), periodValue);
            });
        }
        return earningMap;
    }

    @Override
    public PaymentInfo computeNetPay(PaymentInfo paymentInfo) {
        BigDecimal gross = paymentInfo.getGrossPay() != null
                ? paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY)
                : null;
        if (gross == null) {
            return paymentInfo;
        }
        BigDecimal totalDeduction = paymentInfo.getDeduction() != null
                ? paymentInfo.getDeduction().getOrDefault(MapKeys.TOTAL_DEDUCTION, BigDecimal.ZERO)
                : BigDecimal.ZERO;

        ExchangeInfo exchangeInfo = paymentInfo.getExchangeInfo();
        if (exchangeInfo == null || exchangeInfo.getExchangeRate() == null
                || exchangeInfo.getExchangeRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PayrollValidationException(
                    "Valid exchange rate required to compute net pay for employeeId="
                            + paymentInfo.getEmployeeID());
        }
        BigDecimal exchangeRate = exchangeInfo.getExchangeRate();

        // Local-currency net for company summary totals
        BigDecimal localNetPay = gross.subtract(totalDeduction);
        updateReportSummary(paymentInfo, PayrollSessionHolder.get(), MapKeys.TOTAL_NET_PAY, localNetPay);
        updateReportSummary(paymentInfo, PayrollSessionHolder.get(), MapKeys.TOTAL_GROSS_PAY, gross);

        // Employee-facing net in source currency (HALF_UP — no systematic CEILING bias)
        paymentInfo.setNetPay(localNetPay.divide(exchangeRate, 2, RoundingMode.HALF_UP));
        return paymentInfo;
    }

    @Override
    public PaymentInfo computeTotalNHF(PaymentInfo paymentInfo) {
        EmployeeMetadata meta = resolveEmployeeMetadata(paymentInfo);
        if (meta.getEmployeeType() == EmployeeType.CONTRACT) {
            return paymentInfo;
        }
        if (paymentInfo.getNhf() != null
                && paymentInfo.getNhf().get(MapKeys.NATIONAL_HOUSING_FUND) != null) {
            updateReportSummary(paymentInfo, PayrollSessionHolder.get(), MapKeys.TOTAL_NHF,
                    paymentInfo.getNhf().get(MapKeys.NATIONAL_HOUSING_FUND));
        }
        return paymentInfo;
    }

    private BigDecimal getTotal(Map<String, BigDecimal> input) {
        return roundToTwoDecimalPlaces(input.entrySet().stream()
                .filter(e -> !PayrollMapKeys.TOTAL_PAYE.equalsIgnoreCase(e.getKey()))
                .filter(e -> !PayrollMapKeys.GROSS_SALARY.equalsIgnoreCase(e.getKey()))
                .filter(e -> !"Taxable Gross".equalsIgnoreCase(e.getKey()))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private PaymentSettingsResponse getOffCyclePaymentDetails(PaymentInfo paymentInfo) {
        return paymentInfo.getPaymentSettings().stream()
                .filter(setting -> setting.getType().equals(PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT))
                .findFirst()
                .orElseGet(PaymentSettingsResponse::new);
    }

    private String resolveOffCyclePayeKey(PaymentInfo paymentInfo) {
        return PayrollMapKeys.offCyclePayeKey(getOffCyclePaymentDetails(paymentInfo).getName());
    }

    private Set<PaymentSettingsResponse> getDeductionsForEmployee(PaymentInfo paymentInfo) {
        return paymentInfo.getPaymentSettings().stream()
                .filter(setting -> setting.getType().getDescription().contains("DEDUCTION"))
                .collect(Collectors.toSet());
    }

    private long getMultiplier(PaymentFrequencyEnum paymentFrequencyEnum) {
        if (paymentFrequencyEnum == null) {
            return 1L;
        }
        return switch (paymentFrequencyEnum) {
            case YEARLY -> 1L;
            case MONTHLY -> 12L;
            default -> 1L;
        };
    }

    private EmployeeMetadata resolveEmployeeMetadata(PaymentInfo paymentInfo) {
        if (PayrollCalculationContextHolder.isBound()) {
            EmployeeMetadata cached =
                    PayrollCalculationContextHolder.get().getEmployeeMetadataById()
                            .get(paymentInfo.getEmployeeID());
            if (cached != null) {
                return cached;
            }
        }
        return employeeMetadataService.getByEmployeeId(paymentInfo.getEmployeeID())
                .orElseThrow(() -> new PayrollValidationException(
                        "Employee metadata not found for employeeId=" + paymentInfo.getEmployeeID()
                                + ". Configure employee payroll metadata before running payroll."));
    }

    private List<Loan> resolveLoans(PaymentInfo paymentInfo) {
        if (PayrollCalculationContextHolder.isBound()) {
            return PayrollCalculationContextHolder.get().loansFor(paymentInfo.getEmployeeID());
        }
        LoanFilter loanFilter = new LoanFilter();
        loanFilter.setCompanyId(paymentInfo.getCompanyID());
        loanFilter.setEmployeeId(paymentInfo.getEmployeeID());
        loanFilter.setStatus(LoanStatus.APPROVED);
        return loanService.getLoans(loanFilter, LocalDate.parse(paymentInfo.getStartDate()), Pageable.unpaged())
                .getContent();
    }

    private List<PaymentSettingMetaData> resolvePaymentSettingsMeta(PaymentInfo paymentInfo) {
        if (PayrollCalculationContextHolder.isBound()) {
            return PayrollCalculationContextHolder.get().paymentSettingsFor(paymentInfo.getEmployeeID());
        }
        return paymentSettingMetadataRepo.findByEmployeeIdAndCompanyId(
                paymentInfo.getEmployeeID(), paymentInfo.getCompanyID());
    }

    private Tax resolveTax() {
        if (PayrollCalculationContextHolder.isBound()
                && PayrollCalculationContextHolder.get().getTax() != null) {
            return PayrollCalculationContextHolder.get().getTax();
        }
        Tax tax = taxRepo.findTaxByCountryAndActiveIsTrue(DEFAULT_TAX_COUNTRY);
        if (tax == null) {
            throw new PayrollValidationException("Active tax configuration not found for " + DEFAULT_TAX_COUNTRY);
        }
        return tax;
    }

    private PaymentFrequencyEnum resolveSalaryFrequency(String companyId) {
        if (PayrollCalculationContextHolder.isBound()
                && PayrollCalculationContextHolder.get().getSalaryFrequency() != null) {
            return PayrollCalculationContextHolder.get().getSalaryFrequency();
        }
        return companyMetadataService.getByCompanyId(companyId)
                .map(CompanyMetadata::getSalaryFrequency)
                .orElse(PaymentFrequencyEnum.MONTHLY);
    }

    private long resolvePaymentEntryMultiplier(String companyId) {
        if (PayrollCalculationContextHolder.isBound()
                && PayrollCalculationContextHolder.get().getPaymentEntryMode() != null) {
            return getMultiplier(PayrollCalculationContextHolder.get().getPaymentEntryMode());
        }
        return companyMetadataService.getByCompanyId(companyId)
                .map(CompanyMetadata::getPaymentEntryMode)
                .map(this::getMultiplier)
                .orElse(1L);
    }

    private String resolvePaymentDistributionJson(String companyId) {
        if (PayrollCalculationContextHolder.isBound()) {
            return PayrollCalculationContextHolder.get().getPaymentDistributionJson();
        }
        return companyMetadataService.getByCompanyId(companyId)
                .map(CompanyMetadata::getPaymentDistribution)
                .orElse(null);
    }

    private PaymentFrequencyEnum getOffCyclePaymentFrequency(PaymentInfo paymentInfo) {
        return paymentInfo.getPaymentSettings().stream()
                .filter(setting -> setting.getType() == PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT)
                .map(PaymentSettingsResponse::getSalaryFrequency)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(PaymentFrequencyEnum.YEARLY);
    }

    private BigDecimal getTotalMonthlyTaxFreeAllowance(PaymentInfo paymentInfo) {
        LocalDate start = LocalDate.parse(paymentInfo.getStartDate());
        LocalDate end = LocalDate.parse(paymentInfo.getEndDate());
        return resolvePaymentSettingsMeta(paymentInfo).stream()
                .filter(Objects::nonNull)
                .filter(setting -> !setting.getStartDate().isAfter(start)
                        && !setting.getEndDate().isBefore(end))
                .filter(setting -> "ALLOWANCE".equalsIgnoreCase(setting.getPaymentType()))
                .filter(setting -> Boolean.FALSE.equals(setting.getTaxable()))
                .filter(setting -> setting.getPaymentAmount() != null)
                .map(PaymentSettingMetaData::getPaymentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}

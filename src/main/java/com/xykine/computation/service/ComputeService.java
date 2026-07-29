package com.xykine.computation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykine.computation.entity.CompanyMetadata;
import com.xykine.computation.entity.PaymentSettingMetaData;
import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.entity.PayrollStatus;
import com.xykine.computation.exceptions.IncompleteEntitySetupException;
import com.xykine.computation.exceptions.PayrollUnmodifiableException;
import com.xykine.computation.repo.CompanyMetaDataRepo;
import com.xykine.computation.repo.PaymentSettingMetadataRepo;
import com.xykine.computation.repo.PayrollReportSummaryRepo;
import com.xykine.computation.session.PayrollCalculationContext;
import com.xykine.computation.session.PayrollCalculationContextHolder;
import com.xykine.computation.session.PayrollSessionHolder;
import com.xykine.computation.session.SessionCalculationObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xykine.computation.response.PaymentComputeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.xykine.payroll.model.PaymentFrequencyEnum;
import org.xykine.payroll.model.PaymentInfo;
import org.xykine.payroll.model.PaymentSettingsResponse;
import org.xykine.payroll.model.enums.PaymentTypeEnum;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.xykine.computation.utils.ComputationUtils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComputeService {

    private final PaymentCalculator paymentCalculator;
    private final PayrollReportSummaryRepo payrollReportSummaryRepo;
    private final CompanyMetaDataRepo companyMetaDataRepo;
    private final PaymentSettingMetadataRepo paymentSettingMetadataRepo;
    private final PayrollCalculationContextFactory calculationContextFactory;

    private static final Logger LOGGER = LoggerFactory.getLogger(ComputeService.class);

    public PaymentComputeResponse computePayroll(List<PaymentInfo> rawInfo, SessionCalculationObject session) {
        if(!rawInfo.isEmpty()) {
            LOGGER.debug("First data received {} ", rawInfo.get(0));
        }
        ObjectMapper mapper = new ObjectMapper();
        List<PaymentInfo> paymentInfoList = mapper.convertValue(rawInfo, new TypeReference<List<PaymentInfo>>() {});

        String companyId = paymentInfoList.isEmpty() ? null : paymentInfoList.get(0).getCompanyID();
        PayrollCalculationContext calcContext = companyId != null
                ? calculationContextFactory.build(companyId, paymentInfoList)
                : null;

        List<PaymentInfo> paymentReport = generateReport(paymentInfoList, session, calcContext);
        return  PaymentComputeResponse.builder()
                .message("")
                .success(true)
                .report(paymentReport)
                .build();
    }

    private List<PaymentInfo> generateReport(
            List<PaymentInfo> rawInfo,
            SessionCalculationObject session,
            PayrollCalculationContext calcContext) {
        int cores = Runtime.getRuntime().availableProcessors();
        int size = rawInfo.size();
        int chunkSize = Math.max(1, (size + cores - 1) / cores);
        List<List<PaymentInfo>> chunks = new ArrayList<>();

        for (int i = 0; i < size; i += chunkSize) {
            int end = Math.min(size, i + chunkSize);
            chunks.add(rawInfo.subList(i, end));
        }

        List<CompletableFuture<List<PaymentInfo>>> futures = chunks.stream()
                .map(this::addAdditionalPaymentsIfApplicable)
                .map(finalChunk -> CompletableFuture.supplyAsync(
                        () -> processReport(finalChunk, session, calcContext)))
                .toList();

        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        return allDone.thenApply(v -> futures
                .stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList())
                .join();
    }

    private List<PaymentInfo> processReport(
            List<PaymentInfo> job,
            SessionCalculationObject session,
            PayrollCalculationContext calcContext) {
        PayrollSessionHolder.set(session);
        if (calcContext != null) {
            PayrollCalculationContextHolder.set(calcContext);
        }
        try {
            return job.stream()
                    .map(paymentCalculator::expandPaymentSettingsFromGrossAnnual)
                    .map(paymentCalculator::applyExchange)
                    .map(paymentCalculator::harmoniseToAnnual)
                    .map(paymentCalculator::addPersonalDeduction)
                    .map(paymentCalculator::computeGrossPay)
                    .map(paymentCalculator::computeNonTaxableIncomeExempt)
                    .map(paymentCalculator::computePayeeTax)
                    .map(paymentCalculator::computeTotalDeduction)
                    .map(paymentCalculator::computeNetPay)
                    .map(paymentCalculator::computeTotalNHF)
                    .collect(Collectors.toList());
        } finally {
            PayrollCalculationContextHolder.clear();
            PayrollSessionHolder.clear();
        }
    }

    private List<PaymentInfo> addAdditionalPaymentsIfApplicable(List<PaymentInfo> rawInfo) {
        for (PaymentInfo paymentInfo : rawInfo) {

            List<PaymentSettingsResponse> additionalSettings =
                    paymentSettingMetadataRepo.findByEmployeeIdAndCompanyId(paymentInfo.getEmployeeID(), paymentInfo.getCompanyID())
                            .stream()
                            .filter(Objects::nonNull)
                            .filter(setting -> !setting.getStartDate().isAfter(LocalDate.parse(paymentInfo.getStartDate()))
                                    && !setting.getEndDate().isBefore(LocalDate.parse(paymentInfo.getEndDate())))
                            .filter(setting -> setting.getPaymentAmount() != null)
                            .map(setting -> {
                                paymentInfo.getPaymentSettings().removeIf(existing ->
                                        existing.getName().equalsIgnoreCase(setting.getPaymentName()));

                                return PaymentSettingsResponse.builder()
                                        .active(true)
                                        .employeeID(paymentInfo.getEmployeeID())
                                        .type(PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT)
                                        .salaryFrequency(PaymentFrequencyEnum.MONTHLY)
                                        .value(setting.getPaymentAmount())
                                        .name(setting.getPaymentName())
                                        .build();
                            })
                            .collect(Collectors.toList());
            if (!paymentInfo.isOffCycle()) {
                paymentInfo.getPaymentSettings().addAll(additionalSettings);
            }
        }
        return rawInfo;
    }

    public void validatePayrollIsNotCompleted (String startDate, String companyId) {

        List<PayrollReportSummary> payroll = payrollReportSummaryRepo
                .findAllyByStartDateAndCompanyIdAndOffCycle(startDate, companyId, false);

        payroll.forEach(p -> {
                    if (p == null) {
                        return;
                    }
                    PayrollStatus status = p.getPayrollStatus();
                    if (status == PayrollStatus.COMPLETED
                            || status == PayrollStatus.DISBURSED
                            || status == PayrollStatus.APPROVED
                            || status == PayrollStatus.APPROVED_AUDIT) {
                        throw new PayrollUnmodifiableException(startDate);
                    }
                }
        );

    }

    public void ensurePayrollConfiguration(String companyId) {
        System.out.println(" incomming company id: " + companyId);
        CompanyMetadata companyMetadata = companyMetaDataRepo.findByCompanyId(companyId).orElseThrow(() ->
                new IncompleteEntitySetupException("Please update your payroll configuration in the Payroll Configuration page to continue."));

        if (companyMetadata.getPaymentEntryMode() == null) {
            throw new IncompleteEntitySetupException("Your payroll configuration is missing the Payment Entry Mode (YEARLY or MONTHLY)");
        }
    }

    private PaymentInfo copyPaymentInfo(PaymentInfo original) {
        PaymentInfo copy = new PaymentInfo();
        BeanUtils.copyProperties(original, copy); // Spring utility (shallow copy)
        return copy;
    }

}
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
import com.xykine.computation.utils.ComputationUtils;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(ComputeService.class);

    public PaymentComputeResponse computePayroll(List<PaymentInfo> rawInfo) {

        if(!rawInfo.isEmpty()) {
            LOGGER.debug("First data received {} ", rawInfo.get(0));
        }
            ObjectMapper mapper = new ObjectMapper();
            List<PaymentInfo> paymentInfoList = mapper.convertValue(rawInfo, new TypeReference<List<PaymentInfo>>() {});

        List<PaymentInfo> paymentReport = generateReport(paymentInfoList);
        return  PaymentComputeResponse.builder()
                .message("")
                .success(true)
                .report(paymentReport)
                .build();
    }

    private List<PaymentInfo> generateReport(List<PaymentInfo> rawInfo) {
        int cores = Runtime.getRuntime().availableProcessors();
        int size = rawInfo.size();
        int chunkSize = (size + cores - 1) / cores;
        List<List<PaymentInfo>> chunks = new ArrayList<>();

        for (int i = 0; i < size; i += chunkSize) {
            int end = Math.min(size, i + chunkSize);
            chunks.add(rawInfo.subList(i, end));
        }

        List<CompletableFuture<List<PaymentInfo>>> futures = new ArrayList<>();

        futures.addAll(
                chunks.stream()
                       .map(chunk -> addAdditionalPaymentsIfApplicable(chunk))
                        .map(finalChunk -> CompletableFuture.supplyAsync(() -> processReport(finalChunk)))
                        .toList()
        );

        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        return allDone.thenApply(v -> futures
                .stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList())
                .join();
    }

    private List<PaymentInfo> processReport(List<PaymentInfo> job){
        var payInfos =  job.stream()
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
        return  payInfos;
    }

    private List<PaymentInfo> addAdditionalPaymentsIfApplicable(List<PaymentInfo> rawInfo) {
        for (PaymentInfo paymentInfo : rawInfo) {

            LocalDate paymentStart = LocalDate.parse(paymentInfo.getStartDate());
            LocalDate paymentEnd = LocalDate.parse(paymentInfo.getEndDate());

            List<PaymentSettingsResponse> additionalSettings =
                    paymentSettingMetadataRepo.findByEmployeeIdAndCompanyId(paymentInfo.getEmployeeID(), paymentInfo.getCompanyID())
                            .stream()
                            .filter(Objects::nonNull)
                            .filter(setting -> !setting.getStartDate().isAfter(paymentStart) && !setting.getEndDate().isBefore(paymentEnd))
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

            paymentInfo.getPaymentSettings().addAll(additionalSettings);
        }
        return rawInfo;
    }

    public void validatePayrollIsNotCompleted (String startDate, String companyId) {

        List<PayrollReportSummary> payroll = payrollReportSummaryRepo
                .findAllyByStartDateAndCompanyIdAndOffCycle(startDate, companyId, false);

        payroll.forEach(p -> {
                    if (p != null && (p.getPayrollStatus().compareTo(PayrollStatus.COMPLETED) == 0)) {
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
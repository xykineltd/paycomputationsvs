package com.xykine.computation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.entity.PayrollStatus;
import com.xykine.computation.exceptions.PayrollUnmodifiableException;
import com.xykine.computation.repo.PayrollReportSummaryRepo;
import com.xykine.computation.utils.ComputationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xykine.computation.response.PaymentComputeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.xykine.payroll.model.PaymentFrequencyEnum;
import org.xykine.payroll.model.PaymentInfo;
import org.xykine.payroll.model.PaymentSettingsResponse;
import org.xykine.payroll.model.enums.PaymentTypeEnum;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComputeService {

    private final PaymentCalculator paymentCalculator;
    private final PayrollReportSummaryRepo payrollReportSummaryRepo;

    private static final Logger LOGGER = LoggerFactory.getLogger(ComputeService.class);

    public PaymentComputeResponse computePayroll(List<PaymentInfo> rawInfo) {

        if(rawInfo.size() > 0) {
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
                        .map(chunk -> splitOutOffCycles(chunk))
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
                .map(paymentCalculator::applyExchange)
                .map(paymentCalculator::harmoniseToAnnual)
                .map(paymentCalculator::computeGrossPay)
                .map(paymentCalculator::computeNonTaxableIncomeExempt)
                .map(paymentCalculator::prorateEarnings)
                .map(paymentCalculator::computePayeeTax)
                .map(paymentCalculator::computeTotalDeduction)
                .map(paymentCalculator::computeNetPay)
                .map(paymentCalculator::computeTotalNHF)
                .collect(Collectors.toList());
        return  payInfos;
    }

    private List<PaymentInfo> splitOutOffCycles(List<PaymentInfo> rawInfo) {
        return rawInfo.stream()
                .map(paymentCalculator::expandPaymentSettingsFromGrossAnnual)
                .map(this::splitOffCyclePayments)
                .flatMap(List::stream)
                .toList();
    }

    public List<PaymentInfo> splitOffCyclePayments(PaymentInfo paymentInfo) {

        if (paymentInfo.isOffCycle() || paymentInfo.getPaymentSettings() == null) {
            return List.of(paymentInfo);
        }

        // Extract off-cycle settings
        Set<PaymentSettingsResponse> offCycleSettings = paymentInfo.getPaymentSettings().stream()
                .filter(setting -> setting.getType() == PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT)
                .collect(Collectors.toSet());

        // ✅ If no off-cycle payments, just return the original as-is
        if (offCycleSettings.isEmpty()) {
            return List.of(paymentInfo);
        }

        // Extract regular settings
        Set<PaymentSettingsResponse> regularSettings = paymentInfo.getPaymentSettings().stream()
                .filter(setting -> setting.getType() != PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT)
                .collect(Collectors.toSet());

        // --- Original copy with off-cycle removed ---
        PaymentInfo mainCopy = copyPaymentInfo(paymentInfo);
        mainCopy.setPaymentSettings(regularSettings);
        mainCopy.setOffCycle(false);

        // --- New PaymentInfos for each off-cycle entry ---
        List<PaymentInfo> offCycleCopies = offCycleSettings.stream()
                .map(setting -> {
                    if (setting.getName().equalsIgnoreCase("MONTHLY PERFORMANCE BONUS")) {
                        BigDecimal performanceBonus = ComputationUtils.prorate(mainCopy.getBasicSalary().multiply(setting.getValue().divide(BigDecimal.valueOf(100))),
                                mainCopy.getNumberOfDaysOfUnpaidAbsence(), PaymentFrequencyEnum.MONTHLY);
                        setting.setValue(performanceBonus);
                    }
                    PaymentInfo offCycleCopy = copyPaymentInfo(paymentInfo);
                    offCycleCopy.setPaymentSettings(Set.of(setting));
                    offCycleCopy.setOffCycle(true);
                    return offCycleCopy;
                })
                .toList();

        // Combine original + new copies
        List<PaymentInfo> result = new ArrayList<>();
        result.add(mainCopy);
        result.addAll(offCycleCopies);
        return result;
    }

    public void validatePayrollIsNotApprovedOrCompleted (String startDate, String companyId) {
        PayrollReportSummary payroll = payrollReportSummaryRepo
                .findPayrollReportSummaryByStartDateAndCompanyId(startDate, companyId);
        if (payroll != null && (payroll.getPayrollStatus().compareTo(PayrollStatus.APPROVED) == 0 || payroll.getPayrollStatus().compareTo(PayrollStatus.COMPLETED)  == 0)) {
            throw new PayrollUnmodifiableException(startDate);
        }
    }

    private PaymentInfo copyPaymentInfo(PaymentInfo original) {
        PaymentInfo copy = new PaymentInfo();
        BeanUtils.copyProperties(original, copy); // Spring utility (shallow copy)
        return copy;
    }

}
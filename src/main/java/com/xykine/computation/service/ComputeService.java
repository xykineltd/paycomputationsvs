package com.xykine.computation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xykine.computation.response.PaymentComputeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.xykine.payroll.model.PaymentInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComputeService {

    private final PaymentCalculator paymentCalculator;

    private static final Logger LOGGER = LoggerFactory.getLogger(ComputeService.class);

    public PaymentComputeResponse computePayroll(List<PaymentInfo> rawInfo) {

        if(rawInfo.size() > 0) {
            LOGGER.debug("First data received {} ", rawInfo.get(0));
        }
            ObjectMapper mapper = new ObjectMapper();
            List<PaymentInfo> paymentInfoList = mapper.convertValue(
                    rawInfo,
                    new TypeReference<List<PaymentInfo>>() {
                    }
            );

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

        for (int i = 0; i < chunks.size(); i++) {
            List<PaymentInfo> chunk = chunks.get(i);
            futures.add(CompletableFuture.supplyAsync(() -> processReport(chunk)));
        }

        CompletableFuture<Void> allDone = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        return allDone.thenApply(v -> futures
                .stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList())
                .join();
    }

    private List<PaymentInfo> processReport(List<PaymentInfo> job){
        var payInfos =  job.stream()
                .map(x -> paymentCalculator.applyExchange(x))
                .map(x -> paymentCalculator.harmoniseToAnnual(x))
                .map(x -> paymentCalculator.computeGrossPay(x))
                .map(x -> paymentCalculator.computeNonTaxableIncomeExempt(x))
                .map(x -> paymentCalculator.prorateEarnings(x))
                .map(x -> paymentCalculator.computePayeeTax(x))
                .map(x -> paymentCalculator.computeTotalDeduction(x))
                .map(x -> paymentCalculator.computeNetPay(x))
                .map(x -> paymentCalculator.computeTotalNHF(x))
                .collect(Collectors.toList());
        return  payInfos;
    }
}
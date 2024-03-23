package com.xykine.computation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.request.PaymentInfoRequest;
import com.xykine.computation.response.PaymentComputeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComputeService {

    private final WebClient webClient;
    private final PaymentCalculator paymentCalculator;

    private static final Logger LOGGER = LoggerFactory.getLogger(ComputeService.class);

    public PaymentComputeResponse computePayroll(PaymentInfoRequest paymentComputeRequest) {

        List rawInfo =  webClient
                .post()
                .uri("admin/paymentinfo/compute")
                .body(BodyInserters.fromValue(paymentComputeRequest))
                .retrieve().bodyToMono(List.class).block();

        LOGGER.info("Received data size {} ", rawInfo.size() );
        //LOGGER.info("Received data {} ", rawInfo );

        ObjectMapper mapper = new ObjectMapper();
        List<PaymentInfo> paymentInfoList = mapper.convertValue(
                rawInfo,
                new TypeReference<List<PaymentInfo>>(){}
        );

        // if api call error occurs
        if (rawInfo == null) {
            return  PaymentComputeResponse.builder()
                    .message("error calling Admin service api")
                    .success(false)
                    .report(null)
                    .build();
        }

        List<PaymentInfo> paymentReport = generateReport(paymentInfoList);
        return  PaymentComputeResponse.builder()
                .message("")
                .success(true)
                .report(paymentReport)
                .build();
    }

    public List<PaymentInfo> generateReport(List<PaymentInfo> rawInfo){
        int size = rawInfo.size();
        List<PaymentInfo> job1 = new ArrayList<>();
        List<PaymentInfo> job2 = new ArrayList<>();

        job1.addAll(rawInfo.subList(0, size/2));
        job2.addAll(rawInfo.subList(size/2, size));

        Executor executor = Executors.newFixedThreadPool(10);
        CompletableFuture<List<PaymentInfo>> job1Future = CompletableFuture.supplyAsync(() -> {
            return  processReport(job1);
        }, executor);

        Executor executor2 = Executors.newFixedThreadPool(10);
        CompletableFuture<List<PaymentInfo>> job2Future = CompletableFuture.supplyAsync(() -> {
            return  processReport(job2);
        }, executor2);

        CompletableFuture<List<PaymentInfo>> processReportFuture =job1Future.thenCombine(job2Future, (x, y) -> {
            x.addAll(y);
            return x;
        });
        try {
            return processReportFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private List<PaymentInfo> processReport(List<PaymentInfo> job){
        return  job.stream()
                .filter(x -> x.getEmployeeID() != null)
                .map(x -> paymentCalculator.computeGrossPay(x))
                .map(x -> paymentCalculator.computeNonTaxableIncomeExempt(x))
                .map(x -> paymentCalculator.prorateEarnings(x))
                .map(x -> paymentCalculator.computePayeeTax(x))
                .map(x -> paymentCalculator.computeTotalDeduction(x))
                .map(x -> paymentCalculator.computeNetPay(x))
                .collect(Collectors.toList());
    }
}
package com.xykine.computation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykine.computation.session.SessionCalculationObject;
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

import java.util.List;
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

    private  List<PaymentInfo> generateReport(List<PaymentInfo> rawInfo) {
        return rawInfo
                .stream()
                .map(x -> paymentCalculator.computeGrossPay(x))
                .map(x -> paymentCalculator.computeNonTaxableIncomeExempt(x))
                .map(x -> paymentCalculator.computePayeeTax(x))
                .map(x -> paymentCalculator.computeTotalDeduction(x))
                .map(x -> paymentCalculator.computeNetPay(x))
                .collect(Collectors.toList());
    }
}

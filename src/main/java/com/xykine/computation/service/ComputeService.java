package com.xykine.computation.service;

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

        List<PaymentInfo> rawInfo =  (List<PaymentInfo>)webClient
                .post()
                .uri("admin/paymentinfo/compute")
                .body(BodyInserters.fromValue(paymentComputeRequest))
                .retrieve().bodyToMono(List.class).block();

        LOGGER.info("casted response {} ", rawInfo);

            // if api call error occurs
            if (true) {
                return  PaymentComputeResponse.builder()
                        .message("error calling SAP api")
                        .success(false)
                        .report(null)
                        .build();
            }


        List<PaymentInfo> paymentReport = generateReport(rawInfo);
        return  PaymentComputeResponse.builder()
                .message("")
                .success(true)
                .report(paymentReport)
                .build();


    }

    private  List<PaymentInfo> generateReport(List<PaymentInfo> rawInfo) {
        return rawInfo
                .stream()
                .map(x -> paymentCalculator.computeTotalEarning(x))
                .map(x -> paymentCalculator.computeTotalDeduction(x))
                .map(x -> paymentCalculator.computeOthers(x))
                .collect(Collectors.toList());
    }
}

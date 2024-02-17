package com.xykine.computation.service;

import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.request.PaymentComputeRequest;
import com.xykine.computation.response.PaymentComputeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComputeService {

    private final PaymentCalculator paymentCalculator;

    public PaymentComputeResponse computePayroll(PaymentComputeRequest paymentComputeRequest) {

        WebClient client = WebClient.create("https://126.67.98.43:8080");
        WebClient.UriSpec<WebClient.RequestBodySpec> uriSpec = client.post();
        WebClient.RequestBodySpec bodySpec = uriSpec.uri(URI.create("/resource"));

        WebClient.RequestHeadersSpec<?> headersSpec = bodySpec.body(
                Mono.just(paymentComputeRequest), PaymentComputeRequest.class);

        Mono<List> response = headersSpec.retrieve().bodyToMono(List.class);

        // call api for data
        List<PaymentInfo> rawInfo = response.block();

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

package com.xykine.computation.service;

import com.xykine.computation.request.PaymentInfoRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.xykine.payroll.model.PaymentInfo;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final WebClient webClient;
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminService.class);

//    public List getPaymentInfoList2(PaymentInfoRequest paymentComputeRequest, String token){
//        LOGGER.info(" Flying request {} ", paymentComputeRequest );
//        return  webClient
//                .post()
//                .uri("admin/paymentinfo/compute")
//                .header(HttpHeaders.AUTHORIZATION, token)
//                .body(BodyInserters.fromValue(paymentComputeRequest))
//                .retrieve().bodyToMono(List.class).block();
//    }

    public ResponseEntity<List> getPaymentInfoList(PaymentInfoRequest paymentComputeRequest, String token) {
        LOGGER.info("Flying request {} ", paymentComputeRequest);

        // Retrieve response and inspect headers
        return webClient
                .post()
                .uri("admin/paymentinfo/compute")
                .header(HttpHeaders.AUTHORIZATION, token)
                .bodyValue(paymentComputeRequest)
                .exchangeToMono(response -> {
                    // Extract headers
                    HttpHeaders headers = response.headers().asHttpHeaders();
                    String payrollErrors = headers.getFirst("Payroll-Errors");
                    LOGGER.info("Payroll-Errors: {}", payrollErrors);

                    // Check the status and extract body
                    if (response.statusCode().is2xxSuccessful()) {
                        // Extract the body as a List and combine with headers into a ResponseEntity
                        return response.bodyToMono(List.class)
                                .map(body -> new ResponseEntity<List>(body, headers, response.statusCode()));
                    } else {
                        LOGGER.error("Non-successful response: {}", response.statusCode());
                        return Mono.just(new ResponseEntity<List>(null, headers, response.statusCode())); // Handle non-2xx responses
                    }
                }).block(); // Block to wait for the response
    }

}

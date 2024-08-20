package com.xykine.computation.service;

import com.xykine.computation.request.PaymentInfoRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final WebClient webClient;
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminService.class);

    public List getPaymentInfoList(PaymentInfoRequest paymentComputeRequest, String token){
        LOGGER.info(" Flying request {} ", paymentComputeRequest );
        return  webClient
                .post()
                .uri("admin/paymentinfo/compute")
                .header(HttpHeaders.AUTHORIZATION, token)
                .body(BodyInserters.fromValue(paymentComputeRequest))
                .retrieve().bodyToMono(List.class).block();
    }
}

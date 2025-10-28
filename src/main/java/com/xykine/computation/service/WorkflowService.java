package com.xykine.computation.service;

import com.xykine.computation.exceptions.ApiException;
import com.xykine.computation.exceptions.PayrollValidationException;
import com.xykine.computation.request.PaymentInfoRequest;
import com.xykine.computation.request.StartWorkflowRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
public class WorkflowService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminService.class);

    private final WebClient webClient;

    public WorkflowService(@Qualifier("workflowWebClient") WebClient webClient) {
        this.webClient = webClient;
    }


    public void startWorkflow(StartWorkflowRequest startWorkflowRequest, String token) {
        webClient.post()
                .uri("workflow/start-workflow")
                .header(HttpHeaders.AUTHORIZATION, token)
                .bodyValue(startWorkflowRequest)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        LOGGER.info("Workflow started successfully");
                        return Mono.empty(); // nothing to return on success
                    } else {
                        // Extract error message and throw a custom exception
                        return response.bodyToMono(ApiException.class)
                                .flatMap(errorBody -> {
                                    LOGGER.error("Non-successful response: {}", response.statusCode());
                                    LOGGER.error("Error Message: {}", errorBody.getErrorMessage());
                                    LOGGER.error("Error Code: {}", errorBody.getErrorCode());
                                    return Mono.error(new PayrollValidationException(errorBody.getErrorMessage()));
                                });
                    }
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    LOGGER.error("WebClient call failed: {}", ex.getMessage());
                    return Mono.error(new PayrollValidationException(ex.getMessage()));
                })
                .block(); // Block to make the call synchronous
    }


}

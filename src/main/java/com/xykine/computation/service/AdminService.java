package com.xykine.computation.service;

import com.xykine.computation.dto.EmployeeDetail;
import com.xykine.computation.exceptions.ApiError;
import com.xykine.computation.exceptions.ApiException;
import com.xykine.computation.exceptions.EmployeeFilterException;
import com.xykine.computation.exceptions.PayrollValidationException;
import com.xykine.computation.request.EmployeeFilterRequest;
import com.xykine.computation.request.PaymentInfoRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.xykine.payroll.model.PaymentInfo;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AdminService {

    private final WebClient webClient;

    public AdminService(@Qualifier("adminWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminService.class);

    public List getPaymentInfoList(PaymentInfoRequest paymentComputeRequest, String token) {
        LOGGER.info("Getting payment info for company: {}", paymentComputeRequest.getCompanyId());
        return webClient
                .post()
                .uri("admin/paymentinfo/compute")
                .header(HttpHeaders.AUTHORIZATION, token)
                .bodyValue(paymentComputeRequest)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        // Extract the body as a List if the response is successful
                        return response.bodyToMono(List.class);
                    } else {
                        // Extract error message from the response body and throw custom exception
                        return response.bodyToMono(ApiException.class)
                                .flatMap(errorBody -> {
                                    LOGGER.error("Non-successful response: {}", response.statusCode());
                                    LOGGER.error("Error Message: {}", errorBody.getErrorMessage());
                                    LOGGER.error("Error Code: {}", errorBody.getErrorCode());
                                    LOGGER.error("Error Code: {}", errorBody.getErrorCode());

                                    // Throw custom exception with the error message
                                    return Mono.error(new PayrollValidationException(errorBody.getMessage()));
                                });
                    }
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    // Handle WebClient exceptions, if needed
                    LOGGER.error("WebClient call failed: {}", ex.getMessage());
                    return Mono.error(new PayrollValidationException(ex.getMessage()));
                })
                .block(); // Block to wait for the response
    }

    public List<String> getEmployeeIdListForFilter(EmployeeFilterRequest employeeFilterRequest, String token) {
        return webClient
                .post()
                .uri("admin/employee/get-employee-ids")
                .header(HttpHeaders.AUTHORIZATION, token)
                .bodyValue(employeeFilterRequest)
                .exchangeToMono(response ->{
                    if (response.statusCode().is2xxSuccessful()) {
                        // Extract the body as a List if the response is successful
                        return response.bodyToMono(List.class);
                    } else {
                        // Extract error message from the response body and throw custom exception
                        return response.bodyToMono(ApiException.class)
                                .flatMap(errorBody -> {
                                    LOGGER.error("Non-successful response: {}", response.statusCode());
                                    LOGGER.info("Error Message: {}", errorBody.getErrorMessage());
                                    LOGGER.info("Error Code: {}", errorBody.getErrorCode());

                                    // Throw custom exception with the error message
                                    return Mono.error(new EmployeeFilterException(errorBody.getMessage()));
                                });
                    }
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    // Handle WebClient exceptions, if needed
                    LOGGER.error("WebClient call failed: {}", ex.getMessage());
                    return Mono.error(new EmployeeFilterException(ex.getMessage()));
                })
                .block(); // Block to wait for the response
    }

    public Map<String, List<String>> getCostCenterDetails(EmployeeFilterRequest employeeFilterRequest, String token) {
        return webClient
                .post()
                .uri("admin/employee/cost-centers")
                .header(HttpHeaders.AUTHORIZATION, token)
                .bodyValue(employeeFilterRequest)
                .exchangeToMono(response ->{
                    if (response.statusCode().is2xxSuccessful()) {
                        // Extract the body as a List if the response is successful
                        return response.bodyToMono(Map.class);
                    } else {
                        // Extract error message from the response body and throw custom exception
                        return response.bodyToMono(ApiException.class)
                                .flatMap(errorBody -> {
                                    LOGGER.error("Non-successful response: {}", response.statusCode());
                                    LOGGER.info("Error Message: {}", errorBody.getErrorMessage());
                                    LOGGER.info("Error Code: {}", errorBody.getErrorCode());

                                    // Throw custom exception with the error message
                                    return Mono.error(new EmployeeFilterException(errorBody.getMessage()));
                                });
                    }
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    // Handle WebClient exceptions, if needed
                    LOGGER.error("WebClient call failed: {}", ex.getMessage());
                    return Mono.error(new EmployeeFilterException(ex.getMessage()));
                })
                .block(); // Block to wait for the response
    }

    public Map<String, EmployeeDetail> getEmployeesDetail(EmployeeFilterRequest employeeFilterRequest, String token) {
        return webClient
                .post()
                .uri("admin/employee/employee-details")
                .header(HttpHeaders.AUTHORIZATION, token)
                .bodyValue(employeeFilterRequest)
                .exchangeToMono(response ->{
                    if (response.statusCode().is2xxSuccessful()) {
                        // Extract the body as a List if the response is successful
                        return response.bodyToMono(Map.class);
                    } else {
                        // Extract error message from the response body and throw custom exception
                        return response.bodyToMono(ApiException.class)
                                .flatMap(errorBody -> {
                                    LOGGER.error("Non-successful response: {}", response.statusCode());
                                    LOGGER.info("Error Message: {}", errorBody.getErrorMessage());
                                    LOGGER.info("Error Code: {}", errorBody.getErrorCode());

                                    // Throw custom exception with the error message
                                    return Mono.error(new EmployeeFilterException(errorBody.getMessage()));
                                });
                    }
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    // Handle WebClient exceptions, if needed
                    LOGGER.error("WebClient call failed: {}", ex.getMessage());
                    return Mono.error(new EmployeeFilterException(ex.getMessage()));
                })
                .block(); // Block to wait for the response
    }
}

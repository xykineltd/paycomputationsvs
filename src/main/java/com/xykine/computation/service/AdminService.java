package com.xykine.computation.service;

import com.xykine.computation.dto.EmployeeDetail;
import com.xykine.computation.exceptions.ApiException;
import com.xykine.computation.exceptions.EmployeeFilterException;
import com.xykine.computation.exceptions.PayrollValidationException;
import com.xykine.computation.reconciliation.run.ApplyExcelReconciliationRequest;
import com.xykine.computation.reconciliation.run.ApplyExcelReconciliationResponse;
import com.xykine.computation.request.EmployeeFilterRequest;
import com.xykine.computation.request.PaymentInfoRequest;
import com.xykine.computation.response.PaginatedSelectedEmployeeField;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
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

    public PaginatedSelectedEmployeeField getEmployeeIdListForFilter(
            EmployeeFilterRequest employeeFilterRequest, String token) {

        return webClient
                .post()
                .uri("admin/employee/get-employee-ids")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(employeeFilterRequest)
                .exchangeToMono(response -> {

                    if (response.statusCode().is2xxSuccessful()) {
                        // Deserialize directly into List<CustomEmployeeField>
                        return response.bodyToMono(
                                new ParameterizedTypeReference<PaginatedSelectedEmployeeField>() {}
                        );
                    } else {
                        // Extract API error and convert to custom exception
                        return response.bodyToMono(ApiException.class)
                                .flatMap(errorBody -> {
                                    LOGGER.error("Non-successful response status: {}", response.statusCode());
                                    LOGGER.error("Error Message: {}", errorBody.getErrorMessage());
                                    LOGGER.error("Error Code: {}", errorBody.getErrorCode());

                                    return Mono.error(
                                            new EmployeeFilterException(errorBody.getErrorMessage())
                                    );
                                });
                    }
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    LOGGER.error("WebClient call failed: {}", ex.getResponseBodyAsString());
                    return Mono.error(new EmployeeFilterException(ex.getMessage()));
                })
                .block();   // Blocking for synchronous result
    }

    public Map<String, List<String>> getCostCenterDetails(EmployeeFilterRequest employeeFilterRequest, String token) {
        Map<String, List<String>> noneFound = new HashMap<>();
        noneFound.put("", new ArrayList<>());
        String companyId = employeeFilterRequest == null ? null : employeeFilterRequest.getCompanyID();
        try {
            Map<String, List<String>> result = webClient
                    .post()
                    .uri("admin/employee/cost-centers")
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(employeeFilterRequest)
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful()) {
                            return response.bodyToMono(
                                            new ParameterizedTypeReference<Map<String, List<String>>>() {})
                                    .defaultIfEmpty(noneFound);
                        }
                        LOGGER.warn(
                                "Cost centers not found for company {} (status {}). Continuing payroll without cost centers.",
                                companyId,
                                response.statusCode());
                        return response.releaseBody().thenReturn(noneFound);
                    })
                    .onErrorResume(ex -> {
                        LOGGER.warn(
                                "Cost center lookup failed for company {}: {}. Continuing payroll without cost centers.",
                                companyId,
                                ex.getMessage());
                        return Mono.just(noneFound);
                    })
                    .block();
            if (result == null || result.isEmpty()) {
                return noneFound;
            }
            return result;
        } catch (Exception ex) {
            LOGGER.warn(
                    "Cost center lookup failed for company {}: {}. Continuing payroll without cost centers.",
                    companyId,
                    ex.getMessage());
            return noneFound;
        }
    }

    public Map<String, EmployeeDetail> getEmployeesDetail(EmployeeFilterRequest employeeFilterRequest, String token) {
        return webClient
                .post()
                .uri("/admin/employee/employee-details") // note leading slash if baseUrl is set
                .header(HttpHeaders.AUTHORIZATION, token) // include "Bearer " + token if needed
                .bodyValue(employeeFilterRequest)
                .retrieve()
                .onStatus(
                        status -> !status.is2xxSuccessful(),
                        resp -> resp.bodyToMono(ApiException.class).flatMap(err ->
                                Mono.error(new EmployeeFilterException(err.getMessage()))
                        )
                )
                .bodyToMono(new ParameterizedTypeReference<Map<String, EmployeeDetail>>() {})
                .block(); // keep blocking since method returns Map; consider returning Mono instead
    }

    public ApplyExcelReconciliationResponse applyExcelValues(
            ApplyExcelReconciliationRequest request,
            String token
    ) {
        return webClient
                .post()
                .uri("/admin/payroll-reconciliation/apply-excel-values")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(ApplyExcelReconciliationResponse.class);
                    }
                    return response.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                            .defaultIfEmpty(Map.of())
                            .flatMap(body -> {
                                Object message = body.get("message");
                                if (message == null) {
                                    message = body.get("errorMessage");
                                }
                                String text = message != null
                                        ? String.valueOf(message)
                                        : "Failed to apply Excel values to employee records";
                                LOGGER.error("Apply Excel values failed: {}", text);
                                return Mono.error(new PayrollValidationException(text));
                            });
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    LOGGER.error("Apply Excel values call failed: {}", ex.getResponseBodyAsString());
                    return Mono.error(new PayrollValidationException(
                            ex.getMessage() != null ? ex.getMessage() : "Failed to apply Excel values"));
                })
                .block();
    }
}

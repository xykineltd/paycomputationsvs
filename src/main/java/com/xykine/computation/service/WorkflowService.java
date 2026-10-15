package com.xykine.computation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykine.computation.exceptions.PayrollValidationException;
import com.xykine.computation.request.DeleteStageInstanceRequest;
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

@Slf4j
@Service
public class WorkflowService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public WorkflowService(@Qualifier("workflowWebClient") WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }


    public void startWorkflow(StartWorkflowRequest startWorkflowRequest, String token) {
        webClient.post()
                .uri("workflow/start")
                .header(HttpHeaders.AUTHORIZATION, token)
                .bodyValue(startWorkflowRequest)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        LOGGER.info("Workflow started successfully");
                        return Mono.empty();
                    }
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> {
                                String message = extractErrorMessage(body, "Failed to start payroll workflow");
                                LOGGER.error(
                                        "Workflow start failed status={} payrollId={} body={}",
                                        response.statusCode(),
                                        startWorkflowRequest.getPayrollId(),
                                        body
                                );
                                return Mono.error(new PayrollValidationException(message));
                            });
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    LOGGER.error("WebClient call failed: {}", ex.getMessage());
                    return Mono.error(new PayrollValidationException(ex.getMessage()));
                })
                .block();
    }

    public void deleteWorkflowByReportId(DeleteStageInstanceRequest request, String token) {
        webClient.post()
                .uri("workflow/by-companyId-and-reportId")
                .header(HttpHeaders.AUTHORIZATION, token)
                .bodyValue(request)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        LOGGER.info("Workflow deleted successfully");
                        return Mono.empty();
                    }
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> {
                                String message = extractErrorMessage(body, "Failed to delete payroll workflow");
                                LOGGER.error(
                                        "Workflow delete failed status={} reportId={} body={}",
                                        response.statusCode(),
                                        request.getReportId(),
                                        body
                                );
                                return Mono.error(new PayrollValidationException(message));
                            });
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    LOGGER.error("WebClient call failed: {}", ex.getMessage());
                    return Mono.error(new PayrollValidationException(ex.getMessage()));
                })
                .block();
    }

    private String extractErrorMessage(String body, String fallback) {
        if (body == null || body.isBlank()) {
            return fallback;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String message = textOrNull(root, "message");
            if (message == null) {
                message = textOrNull(root, "errorMessage");
            }
            if (message == null) {
                message = textOrNull(root, "error");
            }
            JsonNode details = root.get("details");
            if (details != null && details.isArray() && details.size() > 0) {
                StringBuilder detailText = new StringBuilder();
                details.forEach(d -> {
                    if (d != null && !d.isNull()) {
                        if (detailText.length() > 0) {
                            detailText.append("; ");
                        }
                        detailText.append(d.asText());
                    }
                });
                if (detailText.length() > 0) {
                    message = (message == null || message.isBlank())
                            ? detailText.toString()
                            : message + " (" + detailText + ")";
                }
            }
            if (message != null && !message.isBlank()) {
                return message;
            }
        } catch (Exception ignored) {
            // fall through to raw body
        }
        return body.length() > 500 ? body.substring(0, 500) : body;
    }

    private static String textOrNull(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }
}

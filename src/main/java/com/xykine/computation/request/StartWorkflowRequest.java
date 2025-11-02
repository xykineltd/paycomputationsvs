package com.xykine.computation.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class StartWorkflowRequest {
    @NotBlank
    private String entity;
    @NotBlank private String companyId;
    @NotBlank private String userId;
    private String payrollId;
    private String payrollType;
    private Instant firstDueDate;
    private Instant secondDueDate;
    private long numberOfEmployees;
    private long numberOfPays;
    private BigDecimal netPay;
    private String createdBy;
}

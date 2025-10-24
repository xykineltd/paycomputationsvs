package com.xykine.computation.request.WorkflowDTOs;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;

@Data
public class StartWorkflowRequest {
    @NotBlank
    private String entity;        // "PAYROLL"
    @NotBlank private String companyId;
    @NotBlank private String userId;        // who is starting
    // Optional: pass a payrollId if you already have one. Otherwise we generate one.
    private String payrollId;
    private Instant firstDueDate;           // optional
    private Instant secondDueDate;          // optional
}
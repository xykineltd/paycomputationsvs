package com.xykine.computation.request.WorkflowDTOs;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UpdateStageRequest {
    // Identify the record to update by id OR by (entity + companyId + stepNumber)
    private String id;

    // If id is absent, these three are required:
    private String entity;      // "PAYROLL"
    private String companyId;
    private Integer stepNumber;

    // Updatable fields:
    private String name;        // optional; update if provided
    private String description; // optional
    private String approverId;  // optional
    private List<PAYROLL_ACTIONS> actions;

    // Optional: allow changing stepNumber (with conflict checks)
    private Integer newStepNumber; // if not null, move stage to a new step
}

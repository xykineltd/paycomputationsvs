package com.xykine.computation.request.WorkflowDTOs;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;

@Data
public class CompleteWorkflowStageInstanceRequest {
    @NotBlank
    private String status;        // "APPROVED" or "REJECTED"
    private String executedById;            // optional: the approver id performing the action
    private String remarks;                 // optional
    private Instant nextDueDate;            // optional due date for the next stage if created
}

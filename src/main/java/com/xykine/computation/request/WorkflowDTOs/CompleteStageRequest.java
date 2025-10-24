package com.xykine.computation.request.WorkflowDTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public
class CompleteStageRequest {
    @NotBlank
    private String executedById;    // approver
    @NotNull
    private Boolean approve;        // true = APPROVED, false = REJECTED
    private String remarks;
}
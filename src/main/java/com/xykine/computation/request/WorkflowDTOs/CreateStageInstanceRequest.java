package com.xykine.computation.request.WorkflowDTOs;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;

@Data
public
class CreateStageInstanceRequest {
    @NotBlank
    private String payrollId;
    @NotBlank private String stageId;
    @NotBlank private String executedById;
    private Instant dueDate;
}
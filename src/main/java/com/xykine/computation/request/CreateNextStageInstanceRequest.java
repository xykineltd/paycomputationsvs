package com.xykine.computation.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;

@Data
public
class CreateNextStageInstanceRequest {
    @NotBlank
    private String payrollId;
    @NotBlank private String executedById;    // the actor initiating the transition
    private Instant dueDate;
}
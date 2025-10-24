package com.xykine.computation.request.WorkflowDTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;


@Data
public class CreateStageRequest {
    @NotBlank private String entity;      // "PAYROLL" (maps to StageEntity)
    @NotBlank private String companyId;
    @NotNull  private Integer stepNumber;
    @NotBlank private String name;
    private String description;
    @NotBlank private String approverId;
    @NotBlank private String approverName;
    private List<PAYROLL_ACTIONS> actions;
}

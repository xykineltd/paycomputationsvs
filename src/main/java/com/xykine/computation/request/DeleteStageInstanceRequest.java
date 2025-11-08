package com.xykine.computation.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class DeleteStageInstanceRequest {
    @NotBlank private String reportId;  
    @NotBlank private String companyId;
}

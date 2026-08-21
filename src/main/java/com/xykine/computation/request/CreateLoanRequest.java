package com.xykine.computation.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateLoanRequest {
    @NotBlank private String companyId;
    @NotBlank private String employeeId;
    @NotNull @DecimalMin("0.0") private BigDecimal principalAmount;
    @DecimalMin("0.0")  private BigDecimal scheduledRepaymentAmount;
    @NotBlank private String description;
}

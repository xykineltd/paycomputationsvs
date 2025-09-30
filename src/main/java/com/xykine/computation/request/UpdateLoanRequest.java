package com.xykine.computation.request;

import com.xykine.computation.domain.LoanStatus;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateLoanRequest {
    private LoanStatus status;
    private Boolean active;
    @DecimalMin("0.0")
    private BigDecimal principalAmount;
}
package com.xykine.computation.request;

import com.xykine.computation.domain.AdjustmentType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdjustLoanRequest {
    @NotNull private AdjustmentType type;       // INCREASE or DECREASE
    @NotNull @DecimalMin("0.01") private BigDecimal amount;
    private String reason;
    private boolean approveNow = true;          // if true, apply immediately
    private String approvedBy;                  // required if approveNow=true
}

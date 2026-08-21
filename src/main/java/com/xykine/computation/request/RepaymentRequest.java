package com.xykine.computation.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class RepaymentRequest {
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;
    private Instant paidAt;       // default now if null
    private String reference;
}

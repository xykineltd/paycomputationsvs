package com.xykine.computation.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentDistributionItem {
    private String type;
    private String name;
    private BigDecimal percentage;
}


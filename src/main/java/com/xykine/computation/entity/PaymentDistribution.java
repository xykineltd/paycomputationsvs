package com.xykine.computation.entity;

import lombok.Data;

@Data
public class PaymentDistribution {
    private String type;
    private double percentage;
    private String name;
}

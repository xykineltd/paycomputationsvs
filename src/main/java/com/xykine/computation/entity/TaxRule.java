package com.xykine.computation.entity;

import lombok.Data;

@Data
public class TaxRule {
    private Long limit;   // nullable
    private double rate;  // percent for this slab
}

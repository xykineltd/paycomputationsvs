package com.xykine.computation.payrollreconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tolerances {
    @Builder.Default
    private double money = 0.01;
    @Builder.Default
    private double days = 0;
    @Builder.Default
    private double factor = 0.0001;
}

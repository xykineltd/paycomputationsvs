package com.xykine.computation.reconciliation.mapping;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationTolerances {
    private Double money;
    private Double days;
    private Double factor;
}

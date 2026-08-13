package com.xykine.computation.reconciliation.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationDetailsResponse {
    private List<PayrollReconciliationDiff> details;
    private long totalItems;
    private int totalPages;
    private int currentPage;
}

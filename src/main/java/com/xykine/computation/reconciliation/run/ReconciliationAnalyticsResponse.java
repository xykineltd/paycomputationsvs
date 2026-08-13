package com.xykine.computation.reconciliation.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationAnalyticsResponse {
    private String reconciliationId;
    private String status;
    private Boolean inputPassed;
    private Boolean outcomePassed;
    private PayrollReconciliationTemp.StageAnalytics input;
    private PayrollReconciliationTemp.StageAnalytics outcome;
}

package com.xykine.computation.payrollreconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {
    private String reconciliationId;
    private String status;
    private boolean inputPassed;
    private boolean outcomePassed;
    private String sheetName;
    private String legalEntityId;
    private String legalEntityName;
    private StageAnalytics input;
    private StageAnalytics outcome;
}

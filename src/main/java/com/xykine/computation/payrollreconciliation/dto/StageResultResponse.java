package com.xykine.computation.payrollreconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageResultResponse {
    private String reconciliationId;
    private String stage;
    private boolean passed;
    private StageAnalytics summary;
}

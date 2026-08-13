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
public class StageRunResponse {
    private String reconciliationId;
    private String sheetName;
    private Integer rowCount;
    private boolean passed;
    private PayrollReconciliationTemp.StageAnalytics summary;
    private String status;
}

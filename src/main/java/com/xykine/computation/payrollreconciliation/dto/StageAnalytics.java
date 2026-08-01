package com.xykine.computation.payrollreconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageAnalytics {
    private long matched;
    private long mismatched;
    private long excelOnly;
    private long systemOnly;
    private long hardFailures;
    private long totalExcel;
    private long totalSystem;
    private long totalCompared;
    private long totalDiffRows;
}

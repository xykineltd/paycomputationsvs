package com.xykine.computation.reconciliation.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSystemResponse {
    private String reconciliationId;
    private int employeesUpdated;
    private int cellsUpdated;
    @Builder.Default
    private List<String> fieldsApplied = new ArrayList<>();
    @Builder.Default
    private List<String> unsupportedFields = new ArrayList<>();
    @Builder.Default
    private List<SkippedEmployee> skippedEmployees = new ArrayList<>();
    private Boolean inputPassed;
    private PayrollReconciliationTemp.StageAnalytics input;
    private String status;
    private String message;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkippedEmployee {
        private String employeeCode;
        private String reason;
    }
}

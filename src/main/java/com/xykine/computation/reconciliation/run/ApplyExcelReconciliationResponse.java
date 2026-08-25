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
public class ApplyExcelReconciliationResponse {
    private int employeesUpdated;
    private int cellsUpdated;
    @Builder.Default
    private List<String> fieldsApplied = new ArrayList<>();
    @Builder.Default
    private List<String> unsupportedFields = new ArrayList<>();
    @Builder.Default
    private List<SkippedEmployee> skippedEmployees = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkippedEmployee {
        private String employeeCode;
        private String reason;
    }
}

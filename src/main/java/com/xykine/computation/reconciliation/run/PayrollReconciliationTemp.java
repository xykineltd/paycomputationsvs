package com.xykine.computation.reconciliation.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parent run document for a payroll reconciliation upload.
 * Collection name mirrors the requested "payroll-reconciliation-temp" table.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document("payrollReconciliationTemp")
@CompoundIndex(name = "company_report_idx", def = "{'companyId': 1, 'reportId': 1}")
public class PayrollReconciliationTemp {
    @Id
    private String id;

    @Indexed
    private String companyId;
    private String reportId;
    /** Optional — which entity alias was used for sheet selection */
    private String legalEntityId;

    private String fileName;
    private String sheetName;
    private Integer rowCount;
    private Integer headerRowIndex;
    private Integer dataStartRow;
    private String excelMatchKey;
    private String systemMatchKey;

    private Map<String, Double> tolerances;
    private List<Map<String, Object>> columnMappings;

    /** UPLOADED | INPUT_DONE | OUTCOME_DONE | FAILED */
    private String status;

    private Boolean inputPassed;
    private Boolean outcomePassed;

    private StageAnalytics inputAnalytics;
    private StageAnalytics outcomeAnalytics;

    private String errorMessage;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StageAnalytics {
        private long matched;
        private long mismatched;
        private long excelOnly;
        private long systemOnly;
        private long hardFailures;
        private long totalDiffRows;
    }
}

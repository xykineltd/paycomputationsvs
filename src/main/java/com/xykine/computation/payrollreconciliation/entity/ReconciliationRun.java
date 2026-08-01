package com.xykine.computation.payrollreconciliation.entity;

import com.xykine.computation.payrollreconciliation.dto.StageAnalytics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "reconciliation_run")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
        @CompoundIndex(name = "company_report_idx", def = "{'companyId': 1, 'reportId': 1}")
})
public class ReconciliationRun {
    @Id
    private String id;
    private String companyId;
    private String reportId;
    private String legalEntityId;
    private String legalEntityName;
    private String sheetName;
    private String status; // UPLOADED | INPUT_DONE | OUTCOME_DONE | FAILED
    private boolean inputPassed;
    private boolean outcomePassed;
    private StageAnalytics inputAnalytics;
    private StageAnalytics outcomeAnalytics;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

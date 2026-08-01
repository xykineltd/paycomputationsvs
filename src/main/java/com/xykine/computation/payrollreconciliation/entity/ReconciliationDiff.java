package com.xykine.computation.payrollreconciliation.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Document(collection = "reconciliation_diff")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
        @CompoundIndex(name = "run_stage_status_idx", def = "{'runId': 1, 'stage': 1, 'status': 1}"),
        @CompoundIndex(name = "run_stage_idx", def = "{'runId': 1, 'stage': 1}")
})
public class ReconciliationDiff {
    @Id
    private String id;
    private String runId;
    private String companyId;
    private String stage; // input | outcome
    private String employeeCode;
    private String employeeName;
    private String status; // MATCH | MISMATCH | EXCEL_ONLY | SYSTEM_ONLY
    private String field;
    private String systemPath;
    private String excelValue;
    private String systemValue;
    private BigDecimal delta;
    private String severity; // hard | soft
    private String valueType;
}

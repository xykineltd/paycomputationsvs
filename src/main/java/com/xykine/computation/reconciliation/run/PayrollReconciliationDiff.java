package com.xykine.computation.reconciliation.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document("payrollReconciliationDiffs")
@CompoundIndex(name = "recon_stage_status_idx", def = "{'reconciliationId': 1, 'stage': 1, 'status': 1}")
public class PayrollReconciliationDiff {
    @Id
    private String id;

    @Indexed
    private String reconciliationId;

    /** input | outcome */
    private String stage;

    /** MATCH | MISMATCH | EXCEL_ONLY | SYSTEM_ONLY */
    private String status;

    private String employeeCode;
    private String employeeName;
    private String field;
    private Object excelValue;
    private Object systemValue;
    private String valueType;
    private Object delta;
    /** hard | soft */
    private String severity;
}

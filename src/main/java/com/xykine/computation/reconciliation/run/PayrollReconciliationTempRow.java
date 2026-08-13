package com.xykine.computation.reconciliation.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raw uploaded Excel row for a reconciliation run.
 * Always replaced in full on each new upload for the same company/report scope.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document("payrollReconciliationTempRows")
@CompoundIndex(name = "recon_match_idx", def = "{'reconciliationId': 1, 'matchKeyValue': 1}")
public class PayrollReconciliationTempRow {
    @Id
    private String id;

    @Indexed
    private String reconciliationId;

    private String companyId;
    private String reportId;

    private Integer rowNumber;
    private String matchKeyValue;

    /** Excel header → raw cell value */
    @Builder.Default
    private Map<String, Object> cells = new LinkedHashMap<>();
}

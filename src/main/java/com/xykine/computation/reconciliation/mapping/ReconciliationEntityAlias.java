package com.xykine.computation.reconciliation.mapping;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationEntityAlias {
    /** Preferred: sheet alias is scoped to the company mapping. */
    private String companyId;
    private String companyName;

    /** Legacy fields — kept for backward-compatible stored mappings. */
    private String legalEntityId;
    private String legalEntityName;
    private String excelSheetName;
    private String excelLegalEntityValue;
}

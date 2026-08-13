package com.xykine.computation.reconciliation.mapping;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationColumnMapping {
    private String excelHeader;
    private String systemPath;
    /** input | outcome */
    private String stage;
    /** hard | soft */
    private String severity;
    private Boolean enabled;
    /** money | number | text | date */
    private String valueType;
    private Boolean isMatchKey;
}

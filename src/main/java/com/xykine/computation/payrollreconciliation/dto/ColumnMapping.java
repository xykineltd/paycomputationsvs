package com.xykine.computation.payrollreconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnMapping {
    private String excelHeader;
    private String systemPath;
    private String stage; // input | outcome
    private String severity; // hard | soft
    private boolean enabled;
    private String valueType; // money | number | text | date
    private boolean isMatchKey;
}

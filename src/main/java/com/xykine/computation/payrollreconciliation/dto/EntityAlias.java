package com.xykine.computation.payrollreconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityAlias {
    private String legalEntityId;
    private String legalEntityName;
    private String excelSheetName;
    private String excelLegalEntityValue;
}

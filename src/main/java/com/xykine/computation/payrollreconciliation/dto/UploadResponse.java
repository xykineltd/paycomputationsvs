package com.xykine.computation.payrollreconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {
    private String reconciliationId;
    private String sheetName;
    private long rowCount;
    private String legalEntityId;
    private String legalEntityName;
}

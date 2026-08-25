package com.xykine.computation.reconciliation.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplyExcelReconciliationRequest {
    private String companyId;
    @Builder.Default
    private List<ExcelFieldUpdate> updates = new ArrayList<>();
}

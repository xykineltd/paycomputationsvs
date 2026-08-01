package com.xykine.computation.payrollreconciliation.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

@Document(collection = "reconciliation_excel_row")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
        @CompoundIndex(name = "run_emp_idx", def = "{'runId': 1, 'employeeCode': 1}")
})
public class ReconciliationExcelRow {
    @Id
    private String id;
    private String runId;
    private String companyId;
    private String employeeCode;
    private String employeeName;
    private int rowNumber;
    @Builder.Default
    private Map<String, String> values = new HashMap<>();
}

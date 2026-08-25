package com.xykine.computation.reconciliation.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcelFieldUpdate {
    private String employeeCode;
    private String field;
    private String systemPath;
    private Object value;
    private String valueType;
}

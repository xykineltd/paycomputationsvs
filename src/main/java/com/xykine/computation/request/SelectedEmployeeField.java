package com.xykine.computation.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SelectedEmployeeField {
    private String employeeID;
    private String fullName;
    private String employeeCode;
    private String startDate;
}


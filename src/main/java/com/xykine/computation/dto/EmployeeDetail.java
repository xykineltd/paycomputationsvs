package com.xykine.computation.dto;

import lombok.Data;

@Data
public class EmployeeDetail {
    private String mappedId;  // MON1928
    private String name;
    private String hireDate;
    private String exitDate;
    private String role;
    private String dateOfBirth;
    private String sex;
    private String stateOfResidence;
    private String taxId;
}

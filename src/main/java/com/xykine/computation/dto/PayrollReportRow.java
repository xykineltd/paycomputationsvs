package com.xykine.computation.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PayrollReportRow {
    private String id;

    private String summaryId;
    private String offCycleId;
    private String companyId;
    private String departmentId;
    private String employeeId;

    private String employeeFullName;
    private String employeeCode;
    private LocalDate hireDate;

    private String startDate;        // PayrollReportDetail.startDate (String in your model)
    private String endDate;          // PayrollReportDetail.endDate (String)
    private Object payrollStatus;    // PayrollStatus or String depending on storage
    private boolean offCycle;
    private boolean payrollSimulation;

    private LocalDateTime createdDate;

    // hydrated
    private Object reportData;       // deserialized
}

package com.xykine.computation.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Document
public class PayrollReportDetail {
    @Id
    private String id;
    private String summaryId;
    private String offCycleId;
    private String companyId;
    private String departmentId;
    private String employeeId;
    private byte[] report;
    private String startDate;
    private String endDate;
    private boolean payrollSimulation;
    private boolean payrollApproved;
    private boolean offCycle;
    private LocalDateTime createdDate;
}

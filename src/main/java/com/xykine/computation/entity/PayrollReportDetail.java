package com.xykine.computation.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.xykine.payroll.model.ExchangeInfo;

import java.time.LocalDateTime;

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
    private String fullName;
    private byte[] report;
    private String startDate;
    private String endDate;
    private boolean payrollSimulation;
    private boolean payrollApproved;
    private boolean offCycle;
    private String currency;
    private ExchangeInfo exchangeInfo;
    private LocalDateTime createdDate;
}

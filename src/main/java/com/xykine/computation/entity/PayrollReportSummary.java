package com.xykine.computation.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.xykine.payroll.model.PaymentFrequencyEnum;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Document
public class PayrollReportSummary {
    @Id
    private UUID id;
    private String companyId;
    private String offCycleId;
    private byte[] report;
    private String startDate;
    private String endDate;
    private boolean payrollSimulation;
    private boolean payrollApproved;
    private boolean payrollCompleted;
    private boolean offCycle;
    private LocalDateTime createdDate;
    private long totalNumberOfEmployees;
    private PaymentFrequencyEnum paymentFrequency;
    @CreatedBy
    private String createdBy;
}

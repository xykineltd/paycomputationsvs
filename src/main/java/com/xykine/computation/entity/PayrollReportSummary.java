package com.xykine.computation.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.xykine.payroll.model.PaymentFrequencyEnum;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Document
@CompoundIndexes({
        @CompoundIndex(name = "company_start_offcycle_idx", def = "{'companyId': 1, 'startDate': 1, 'offCycle': 1}"),
        @CompoundIndex(name = "company_status_idx", def = "{'companyId': 1, 'payrollStatus': 1}")
})
public class PayrollReportSummary {
    @Id
    private UUID id;
    private String companyId;
    private String offCycleId;
    private byte[] report;
    private String startDate;
    private String endDate;
    private boolean payrollSimulation;
    private PayrollStatus payrollStatus;
    private boolean offCycle;
    private LocalDateTime createdDate;
    private long totalNumberOfEmployees;
    private PaymentFrequencyEnum paymentFrequency;
    private String code;
    @CreatedBy
    private String createdBy;
}

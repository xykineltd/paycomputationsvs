package com.xykine.computation.entity;

import com.xykine.computation.model.MapKeys;
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
public class PayrollReportSummary {
    @Id
    private UUID id;
    private String companyId;
    private String offCycleId;
    private byte[] report;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean payrollSimulation;
    private boolean payrollApproved;
    private boolean offCycle;
    private LocalDateTime createdDate;
}

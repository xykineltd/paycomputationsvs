package com.xykine.computation.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReportAnalytics {
    private String payPeriod;
    private long numberOfEmployees;
    private long numberOfPays;
    private BigDecimal netPay;
    private String status;
    private String reportId;
    private String companyId;
    private boolean offCycle;
    private String offCycleId;
    private String payrollType;
    private String createdDate;
}

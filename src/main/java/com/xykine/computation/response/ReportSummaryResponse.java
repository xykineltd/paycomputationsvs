package com.xykine.computation.response;

import lombok.Builder;
import lombok.Data;
import org.xykine.payroll.model.PaymentFrequencyEnum;

import java.math.BigDecimal;


@Builder
@Data
public class ReportSummaryResponse {
    private String reportId;
    private String companyId;
    private String offCycleId;
    private String payrollStatus;
    private String startDate;
    private String endDate;
    private String createdDate;
    private boolean payrollSimulated;
    private boolean offCycle;
    private long totalNumberOfEmployees;
    private BigDecimal grossPay;
    private String code;
}
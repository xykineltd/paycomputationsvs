package com.xykine.computation.request;

import com.xykine.computation.entity.PayrollStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CompletePayrollResponse {
    private String reportId;
    private String companyId;
    private PayrollStatus  payrollStatus;
    private String  completedDate;
    private String  startDate;
    private String  endDate;
    private boolean  offCycle;
    private String  code;
    private Map<String, BigDecimal> summary;
}

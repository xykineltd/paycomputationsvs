package com.xykine.computation.request;

import com.xykine.computation.entity.PayrollStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UpdateReportRequest {
    String startDate;
    String companyId;
    String reportId;
    String offCycleId;
    PayrollStatus payrollStatus;
    boolean cancelPayroll;
    boolean offCycle;
}

package com.xykine.computation.request;

import com.xykine.computation.entity.PayrollStatus;
import lombok.Data;

@Data
public class UpdateReportRequest {
    String startDate;
    String companyId;
    String offCycleId;
    PayrollStatus payrollStatus;
    boolean cancelPayroll;
    boolean offCycle;
}

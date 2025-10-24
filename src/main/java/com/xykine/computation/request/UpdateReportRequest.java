package com.xykine.computation.request;

import com.xykine.computation.entity.PayrollStatus;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateReportRequest {
    UUID reportId;
    String startDate;
    String companyId;
    String offCycleId;
    PayrollStatus payrollStatus;
    boolean cancelPayroll;
    boolean offCycle;
}

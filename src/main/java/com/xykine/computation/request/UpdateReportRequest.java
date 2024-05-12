package com.xykine.computation.request;

import lombok.Data;

@Data
public class UpdateReportRequest {
    String startDate;
    String companyId;
    String offCycleId;
    boolean payrollApproved;
    boolean cancelPayroll;
    boolean offCycle;
}

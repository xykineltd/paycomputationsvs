package com.xykine.computation.request;

import lombok.Data;

@Data
public class UpdateReportRequest {
    String startDate;
    String companyId;
    boolean payrollApproved;
    boolean cancelPayroll;
}

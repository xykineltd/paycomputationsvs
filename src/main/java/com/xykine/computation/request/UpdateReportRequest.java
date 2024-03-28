package com.xykine.computation.request;

import lombok.Data;

@Data
public class UpdateReportRequest {
    String startDate;
    boolean payrollApproved;
    boolean cancelPayroll;
}

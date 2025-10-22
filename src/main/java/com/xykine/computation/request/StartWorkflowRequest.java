package com.xykine.computation.request;

import lombok.Data;

@Data
public class StartWorkflowRequest {
    private String reportId;
    private String startDate;
    private String endDate;
    private String payrollType;
    private String userId;
}

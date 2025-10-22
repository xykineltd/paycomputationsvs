package com.xykine.computation.request;

import lombok.Data;

@Data
public class UpdateReportStatus {
    private String reportId;
    private String status;
}

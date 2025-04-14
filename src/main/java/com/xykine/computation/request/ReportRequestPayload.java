package com.xykine.computation.request;

import lombok.Data;

import java.util.List;

@Data
public class ReportRequestPayload {
    private boolean isAllEmployee;
    private List<String> employeeIds;
    private List<String> selectedHeader;

    private String companyId;
    private String reportId;
}
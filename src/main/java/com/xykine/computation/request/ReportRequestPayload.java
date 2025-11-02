package com.xykine.computation.request;

import lombok.Data;

import java.util.List;

@Data
public class ReportRequestPayload {
    private boolean all;
    private List<String> ids;
    private List<String> headers;
    private String companyID;
    private String entityType;
    private DateRange dateRange;
    private String docType;
    private boolean defaultHeaders;
}

package com.xykine.computation.service;

import com.xykine.computation.request.ReportRequestPayload;

import java.util.Set;

public interface ReportGeneratorService {

    void generateReport(ReportRequestPayload reportRequestPayload);
    Set<String> getHeadersForReport(String companyId, String reportId );

}

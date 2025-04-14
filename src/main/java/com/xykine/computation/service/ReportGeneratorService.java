package com.xykine.computation.service;

import com.xykine.computation.request.ReportRequestPayload;
import com.xykine.computation.request.RetrievePaymentElementPayload;
import com.xykine.computation.request.RetrieveSummaryElementRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;


public interface ReportGeneratorService {

    void generateReport(ReportRequestPayload reportRequestPayload);
    Set<String> getHeadersForReport(String companyId, String reportId );
    List<Map<String, Object>> retrievePaymentElementFromReport(RetrievePaymentElementPayload retrievePaymentElementPayload);
    Map<String, Object> extractDataFromSummary(RetrieveSummaryElementRequest request);
}

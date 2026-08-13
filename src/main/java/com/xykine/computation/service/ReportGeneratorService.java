package com.xykine.computation.service;

import com.xykine.computation.request.ReportRequestPayload;
import com.xykine.computation.request.RetrievePaymentElementPayload;
import com.xykine.computation.request.RetrieveSummaryElementRequest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;


public interface ReportGeneratorService {

    byte[] generateReport(ReportRequestPayload reportRequestPayload, String token) throws IOException;
    Set<String> getHeadersForReport(String companyId, String reportId );
    List<Map<String, Object>> retrievePaymentElementFromReport(RetrievePaymentElementPayload retrievePaymentElementPayload);
    Map<String, Object> extractDataFromSummary(RetrieveSummaryElementRequest request);

    /**
     * Same PaymentInfo flattening used by {@link #generateReport} for entityType=details:
     * load report details by companyId + reportId (summaryId), transform, extractDetail, swapKey.
     */
    List<Map<String, Object>> loadPaymentInfoRowsForReport(String companyId, String reportId, String token);
}

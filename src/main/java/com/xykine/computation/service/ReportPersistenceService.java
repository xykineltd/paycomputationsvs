package com.xykine.computation.service;

import com.xykine.computation.entity.PayrollReport;
import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.request.UpdateReportRequest;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.response.ReportResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ReportPersistenceService {
    ReportResponse serializeAndSaveReport(PaymentComputeResponse paymentComputeResponse, Long companyId) throws IOException, ClassNotFoundException;
    ReportResponse getPayRollReport(String startData);
    List<ReportResponse> getPayRollReports();
    PayrollReportSummary updateReport(UpdateReportRequest updateReportRequest);
    Map<String, Object> getPaymentDetails(String id, int page, int size);
}

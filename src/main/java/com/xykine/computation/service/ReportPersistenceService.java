package com.xykine.computation.service;

import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.request.UpdateReportRequest;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.response.ReportAnalytics;
import com.xykine.computation.response.ReportResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ReportPersistenceService {
    ReportResponse serializeAndSaveReport(PaymentComputeResponse paymentComputeResponse, String companyId) throws IOException, ClassNotFoundException;
    ReportResponse getPayRollReport(String startData, String companyId);
    List<ReportResponse> getPayRollReports(String companyId);
    PayrollReportSummary approveReport(UpdateReportRequest updateReportRequest);
    boolean deleteReport(UpdateReportRequest updateReportRequest);
    Map<String, Object> getPaymentDetails(String id, String companyId, int page, int size);
    ReportResponse getPaymentDetailsByEmployee(String employeeId, String startDate, String companyId);
    List<ReportAnalytics> getReportAnalytics(String companyId);
}

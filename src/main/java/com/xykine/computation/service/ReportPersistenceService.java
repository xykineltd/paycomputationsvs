package com.xykine.computation.service;

import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.entity.YTDReport;
import com.xykine.computation.request.ReportByTypeRequest;
import com.xykine.computation.request.UpdateReportRequest;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.response.ReportAnalytics;
import com.xykine.computation.response.ReportResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ReportPersistenceService {
    ReportResponse serializeAndSaveReport(PaymentComputeResponse paymentComputeResponse, String companyId) throws IOException, ClassNotFoundException;
    ReportResponse getPayRollReport(String startData, String companyId);

    ReportResponse getPayRollReport(UUID reportId, boolean isSimulate);
    List<ReportResponse> getPayRollReports(String companyId);
    List<ReportResponse> getPayRollReportsByStatus(String companyId, String status);
    Map<String, Object> getReportByEmployeeID(String companyId, String employeeID, int page, int size);
    PayrollReportSummary approveReport(UpdateReportRequest updateReportRequest);
    boolean deleteReport(UpdateReportRequest updateReportRequest);
    PayrollReportSummary completeReport(UpdateReportRequest updateReportRequest);
    Map<String, Object> getPaymentDetails(String id, String companyId, String fullName, int page, int size);
    ReportResponse getPaymentDetailsByEmployee(String employeeId, String startDate, String companyId);
    List<ReportAnalytics> getReportAnalytics(String companyId);
    YTDReport getYTDReport(String employeeId, String companyId);
    Map<String, Object> getPaymentDetailForDates(String employeeId, String companyId, List<String> endDates,  int page, int size);
    Map<String, Object> getPayRollReportByType(ReportByTypeRequest request, int page, int size);
    Map<String, Object> getPayRollReportDetailByType(ReportByTypeRequest request, int page, int size);

}

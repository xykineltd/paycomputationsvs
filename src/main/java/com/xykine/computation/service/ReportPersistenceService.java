package com.xykine.computation.service;

import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.entity.YTDReport;
import com.xykine.computation.request.*;
import com.xykine.computation.request.PaymentInfoRequest;
import com.xykine.computation.request.ReportByTypeRequest;
import com.xykine.computation.request.UpdatePayrollStatusRequest;
import com.xykine.computation.request.UpdateReportRequest;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.response.ReportAnalytics;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.response.SummaryDetail;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public interface ReportPersistenceService {
    ConcurrentHashMap<String, Set<SummaryDetail>> getSummaryVarianceDetails(String reportId, List<String> employeeIds, String header);
    void computePayrollAsync(Consumer<JobStatusStore> progressCallback, String jobId, String authorizationHeader, PaymentInfoRequest paymentRequest);
    void computeOffCyclePayrollAsync(Consumer<JobStatusStore> progressCallback, String jobId, String authorizationHeader, PaymentInfoRequest paymentRequest);
    ReportResponse serializeAndSaveReport(PaymentComputeResponse paymentComputeResponse, String companyId) throws IOException, ClassNotFoundException;
    ReportResponse getPayRollReport(String startData, String companyId);
    ReportResponse getPayRollReport(UUID reportId);
    List<ReportResponse> getPayRollReportsByStatus(String companyId, String status);
    Map<String, Object> getReportByEmployeeID(String companyId, String employeeID, int page, int size);
    Map<String, Object> getReportByEmployeeIDList(String companyId, List<String> employeeIDList, String summaryId, int page, int size);
    PayrollReportSummary approveReport(UpdateReportRequest updateReportRequest);
    public void updateReportStatus(UpdatePayrollStatusRequest request);
    boolean deleteReport(UpdateReportRequest updateReportRequest, String token);
    CompletePayrollResponse completeReport(CompletePayrollRequest updateReportRequest);
    Map<String, Object> getPaymentDetails(String id, String companyId, String fullName, int page, int size);
    ReportResponse getPaymentDetailsByEmployee(String employeeId, String startDate, String companyId);
    List<ReportAnalytics> getReportAnalytics(String companyId, int page, int size);
    YTDReport getYTDReport(String employeeId, String companyId);
    Map<String, Object> getPaymentDetailForDates(String employeeId, String companyId, List<String> endDates,  int page, int size);
    Map<String, Object> getPayRollReportByType(ReportByTypeRequest request, int page, int size);
    Map<String, Object> getPayRollReportDetailByType(ReportByTypeRequest request, int page, int size);

}

package com.xykine.computation.service;

import com.xykine.computation.entity.PayrollReport;
import com.xykine.computation.request.UpdateReportRequest;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.response.ReportResponse;

import java.io.IOException;
import java.util.List;

public interface ReportPersistenceService {
    void serializeAndSaveReport(PaymentComputeResponse paymentComputeResponse) throws IOException, ClassNotFoundException;
    ReportResponse getPayRollReport(String startData);
    List<ReportResponse> getPayRollReports();
    PayrollReport updateReport(UpdateReportRequest updateReportRequest);
}

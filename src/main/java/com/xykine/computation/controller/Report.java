package com.xykine.computation.controller;

import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.request.UpdateReportRequest;
import com.xykine.computation.response.ReportAnalytics;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.service.ReportPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/compute/reports")
@RequiredArgsConstructor
public class Report {

    private final ReportPersistenceService reportPersistenceService;

    @GetMapping("/{companyId}")
    public List<ReportResponse> getReports(@PathVariable String companyId) {
        return reportPersistenceService.getPayRollReports(companyId);
    }

    @GetMapping("/analytics/{companyId}")
    public List<ReportAnalytics> getAnalyticsReports(@PathVariable String companyId) {
        return reportPersistenceService.getReportAnalytics(companyId);
    }

    @GetMapping("/get-by-start-date/{companyId}/{startDate}")
    public ReportResponse getReport(@PathVariable String startDate, @PathVariable String companyId) {
        return reportPersistenceService.getPayRollReport(startDate, companyId);
    }

    @PutMapping("/approve")
    public boolean approveReport(@RequestBody UpdateReportRequest request) {
        PayrollReportSummary payrollReport = reportPersistenceService.approveReport(request);
        if (payrollReport.isPayrollApproved() != request.isPayrollApproved())
            return false;
        return true;
    }

    @PutMapping("/cancel")
    public boolean deleteReport(@RequestBody UpdateReportRequest request) {
        return reportPersistenceService.deleteReport(request);
    }

    @GetMapping("/paymentDetails")
    public ResponseEntity<?> getPaymentDetails(
            @RequestParam() String id,
            @RequestParam() String companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size) {
        Map<String, Object> response = reportPersistenceService.getPaymentDetails(id, companyId, page, size);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}

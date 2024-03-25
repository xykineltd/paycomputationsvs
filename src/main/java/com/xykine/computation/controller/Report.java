package com.xykine.computation.controller;

import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.request.UpdateReportRequest;
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

    @GetMapping()
    public List<ReportResponse> getReports() {
        return reportPersistenceService.getPayRollReports();
    }

    @PostMapping("/get-by-start-date")
    public ReportResponse getReport(@RequestBody String startDate) {
        return reportPersistenceService.getPayRollReport(startDate);
    }

    @PutMapping("/approve")
    public boolean updateReport(@RequestBody UpdateReportRequest request) {
        PayrollReportSummary payrollReport = reportPersistenceService.updateReport(request);
        if (payrollReport.isPayrollApproved() != request.isPayrollApproved())
            return false;
        return true;
    }

    @GetMapping("/paymentDetails")
    public ResponseEntity<Map<String, Object>> getPaymentDetails(
            @RequestParam(required = true) String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size) {
        Map<String, Object> response = reportPersistenceService.getPaymentDetails(id, page, size);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}

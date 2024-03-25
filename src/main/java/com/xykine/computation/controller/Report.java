package com.xykine.computation.controller;

import com.xykine.computation.entity.PayrollReport;
import com.xykine.computation.request.UpdateReportRequest;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.service.ReportPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        PayrollReport payrollReport = reportPersistenceService.updateReport(request);
        if (payrollReport.isPayrollApproved() != request.isPayrollApproved())
            return false;
        return true;
    }
}

package com.xykine.computation.controller;

import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.entity.YTDReport;
import com.xykine.computation.request.ReportByTypeRequest;
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
        //TODO add summaryVariance field that give the difference between the current and previuos summary values
        return reportPersistenceService.getPayRollReports(companyId);
    }

    @GetMapping("/{companyId}/{employeeId}")
    public ResponseEntity<?> getReportByEmployeeID(
            @PathVariable String companyId,
            @PathVariable String employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size
            ) {
        //TODO add summaryVariance field that give the difference between the current and previuos summary values
        Map<String, Object> response =  reportPersistenceService.getReportByEmployeeID(companyId,  employeeId, page, size);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/analytics/{companyId}")
    public List<ReportAnalytics> getAnalyticsReports(@PathVariable String companyId) {
        return reportPersistenceService.getReportAnalytics(companyId);
    }

    @GetMapping("/get-by-start-date/{companyId}/{startDate}")
    public ReportResponse getReport(@PathVariable String startDate, @PathVariable String companyId) {
        return reportPersistenceService.getPayRollReport(startDate, companyId);
    }

    @PostMapping("/get-by-start-date-and-category")
    public List<ReportResponse> getReportByType(@RequestBody ReportByTypeRequest request) {
        return reportPersistenceService.getPayRollReportByType(request);
    }

    @PostMapping("/get-by-start-date-and-employeeId")
    public List<ReportResponse> getPayRollReportDetailByType(@RequestBody ReportByTypeRequest request) {
        return reportPersistenceService.getPayRollReportDetailByType(request);
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

    @PutMapping("/post-to-finance")
    public boolean completeReport(@RequestBody UpdateReportRequest request) {
        PayrollReportSummary payrollReport = reportPersistenceService.completeReport(request);
        if (payrollReport.isPayrollCompleted() != request.isPayrollCompleted())
            return false;
        return true;
    }

    @GetMapping("/paymentDetails")
    public ResponseEntity<?> getPaymentDetails(
            @RequestParam() String id,
            @RequestParam() String companyId,
            @RequestParam(defaultValue = "") String fullName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size) {
        Map<String, Object> response = reportPersistenceService.getPaymentDetails(id, companyId, fullName, page, size);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/paymentDetails/get-by-employee/by-end-dates")
    public ResponseEntity<?> getPaymentDetailsByEmployeeByDates(
            @RequestParam() String employeeId,
            @RequestParam() String companyId,
            @RequestParam() List<String> endDates,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size
            ) {
        Map<String, Object> response  = reportPersistenceService
                .getPaymentDetailForDates(employeeId, companyId, endDates, page, size);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/paymentDetails/get-by-employee")
    public ResponseEntity<?> getPaymentDetailsByEmployee(
            @RequestParam() String companyId,
            @RequestParam() String startDate,
            @RequestParam() String employeeId
    ) {
        ReportResponse response = reportPersistenceService
                .getPaymentDetailsByEmployee(employeeId, startDate, companyId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/ytdReport")
    public ResponseEntity<?> getYtdReport(
            @RequestParam() String employeeId,
            @RequestParam() String companyId
    ) {
        YTDReport response = reportPersistenceService.getYTDReport(employeeId, companyId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}

package com.xykine.computation.controller;

import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.entity.YTDReport;
import com.xykine.computation.request.ReportByTypeRequest;
import com.xykine.computation.request.ReportRequestPayload;
import com.xykine.computation.request.UpdateReportRequest;
import com.xykine.computation.response.ReportAnalytics;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.service.ReportGeneratorService;
import com.xykine.computation.service.ReportPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/compute/reports")
@RequiredArgsConstructor
public class Report {

    private final ReportPersistenceService reportPersistenceService;
    private final ReportGeneratorService reportGeneratorService;

    @GetMapping("/{companyId}/")
    public List<ReportResponse> getReports(@PathVariable String companyId) {
        //TODO add summaryVariance field that give the difference between the current and previuos summary values
        return reportPersistenceService.getPayRollReports(companyId);
    }

    @GetMapping("/{companyId}/status/{status}")
    public List<ReportResponse> getReportsByStatus(@PathVariable String companyId, @PathVariable String status) {
        //TODO add summaryVariance field that give the difference between the current and previuos summary values
        return reportPersistenceService.getPayRollReportsByStatus(companyId, status);
    }

    @GetMapping("/by-reportId/{reportId}/isSimulate/{isSimulate}")
    public ReportResponse getReportsByStatus( @PathVariable UUID reportId, @PathVariable boolean isSimulate) {
        //TODO add summaryVariance field that give the difference between the current and previuos summary values
        return reportPersistenceService.getPayRollReport(reportId, isSimulate);
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
    public List<ReportAnalytics> getAnalyticsReports(@PathVariable String companyId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "12") int size
    ) {
        return reportPersistenceService.getReportAnalytics(companyId, page, size);
    }

    @GetMapping("/get-by-start-date/{companyId}/{startDate}")
    public ReportResponse getReport(@PathVariable String startDate, @PathVariable String companyId) {
        return reportPersistenceService.getPayRollReport(startDate, companyId);
    }

    @PostMapping("/get-by-start-date-and-category")
    public Map<String, Object> getReportByType(
            @RequestBody ReportByTypeRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return reportPersistenceService.getPayRollReportByType(request, page, size);
    }

    @PostMapping("/get-by-start-date-and-employeeId")
    public Map<String, Object> getPayRollReportDetailByType(
            @RequestBody ReportByTypeRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return reportPersistenceService.getPayRollReportDetailByType(request, page, size);
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
    public PayrollReportSummary completeReport(@RequestBody UpdateReportRequest request) {
        return reportPersistenceService.completeReport(request);
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

    // fire and forget
    @PostMapping("/upload-report")
    public void uploadReport(@RequestBody ReportRequestPayload payload){
        reportGeneratorService.generateReport(payload);
    }

    @GetMapping("/payment-header-options/company-id/{companyID}/report-id/{reportId}")
    public Set<String> getAllHeadersForReport(@PathVariable String companyID, @PathVariable String reportId) {
         return reportGeneratorService.getHeadersForReport(companyID, reportId);
    }
}

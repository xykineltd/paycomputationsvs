package com.xykine.computation.controller;

import com.xykine.computation.entity.YTDReport;
import com.xykine.computation.exceptions.PayrollValidationException;
import com.xykine.computation.request.*;

import com.xykine.computation.response.ReportAnalytics;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.response.SummaryDetail;
import com.xykine.computation.service.AdminService;
import com.xykine.computation.service.ReportGeneratorService;
import com.xykine.computation.service.ReportPersistenceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/compute/reports")
@RequiredArgsConstructor
public class Report {

    private final ReportPersistenceService reportPersistenceService;
    private final ReportGeneratorService reportGeneratorService;
    private final AdminService adminService;

    private static final Logger LOGGER = LoggerFactory.getLogger(Report.class);

    @GetMapping("/{companyId}/")
    public List<ReportResponse> getReports(@PathVariable String companyId) {
        return reportPersistenceService.getPayRollReports(companyId);
    }

    @GetMapping("/{companyId}/status/{status}")
    public List<ReportResponse> getReportsByStatus(@PathVariable String companyId, @PathVariable String status) {
        return reportPersistenceService.getPayRollReportsByStatus(companyId, status);
    }

    @PostMapping("/by-reportId/{reportId}")
    public ReportResponse getReport( @RequestBody RetrieveSummaryElementRequest request) {
        return reportPersistenceService.getPayRollReport(UUID.fromString(request.getReportId()));
    }

    @GetMapping("/{companyId}/{employeeId}")
    public ResponseEntity<?> getReportByEmployeeID(
            @PathVariable String companyId,
            @PathVariable String employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size
            ) {
        Map<String, Object> response =  reportPersistenceService.getReportByEmployeeID(companyId,  employeeId, page, size);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/filterReports")
    public ResponseEntity<?> getReportByFilter(
            @RequestBody EmployeeFilterRequest employeeFilterRequest,
            @RequestHeader("Authorization") String authorizationHeader) {
        List<String> filteredList = adminService.getEmployeeIdListForFilter(employeeFilterRequest, authorizationHeader);
        Map<String, Object> response =  reportPersistenceService.getReportByEmployeeIDList(employeeFilterRequest.getCompanyID(),
                filteredList, employeeFilterRequest.getReportId(),
                employeeFilterRequest.getPage(), employeeFilterRequest.getSize());
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

    @PutMapping("/update-report-status")
    public void updateStatus(@RequestBody UpdatePayrollStatusRequest request) {
        try {
            reportPersistenceService.updateReportStatus(request);
        } catch (IllegalArgumentException e) {
            throw new PayrollValidationException("Invalid payroll status: " + request.getStatus());
        }
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

    @PostMapping("/download-report")
    public ResponseEntity<byte[]> uploadReport(@RequestBody ReportRequestPayload payload,
                                               @RequestHeader("Authorization") String authorizationHeader
                                               ) throws IOException {
        return new ResponseEntity<>(reportGeneratorService.generateReport(payload, authorizationHeader), HttpStatus.OK);
    }

    @PostMapping("/retrieve-payment-element")
    public List<Map<String, Object>> getPaymentElement(@RequestBody RetrievePaymentElementPayload retrievePaymentElementPayload){
        return reportGeneratorService.retrievePaymentElementFromReport(retrievePaymentElementPayload);
    }

    @GetMapping("/payment-header-options/company-id/{companyID}/report-id/{reportId}")
    public Set<String> getAllHeadersForReport(@PathVariable String companyID, @PathVariable String reportId) {
         return reportGeneratorService.getHeadersForReport(companyID, reportId);
    }

    @PostMapping("/total-netpay-by-report-id")
    public Map<String, Object> getTotalNetPayByReportId(@RequestBody RetrieveSummaryElementRequest request){
        return reportGeneratorService.extractDataFromSummary(request);
    }

    @PostMapping("/variance-details")
    public ResponseEntity<?> getVarianceDetails(
            @RequestBody EmployeeFilterRequest employeeFilterRequest,
            @RequestHeader("Authorization") String authorizationHeader) {
        List<String> filteredList = adminService.getEmployeeIdListForFilter(employeeFilterRequest, authorizationHeader);
        ConcurrentHashMap<String, Set<SummaryDetail>> response = reportPersistenceService.getSummaryVarianceDetails(employeeFilterRequest.getReportId(), filteredList, employeeFilterRequest.getHeader());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/variance-details-customized")
    public ResponseEntity<?> getVarianceDetailsCustomized(
            @RequestBody CustomizedVarianceRequest request) {
        Map<String, Map<String, BigDecimal>> response = reportPersistenceService.getSummaryVarianceDetails(request.getReportId(), request.getEmployeeIds());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}

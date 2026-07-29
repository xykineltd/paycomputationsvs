package com.xykine.computation.controller;

import com.xykine.computation.entity.YTDReport;
import com.xykine.computation.exceptions.PayrollValidationException;
import com.xykine.computation.request.*;

import com.xykine.computation.response.*;
import com.xykine.computation.service.AdminService;
import com.xykine.computation.service.ReportGeneratorService;
import com.xykine.computation.service.ReportPersistenceService;
import com.xykine.computation.utils.CompanyAccessGuard;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/compute/reports")
@RequiredArgsConstructor
public class Report {

    private final ReportPersistenceService reportPersistenceService;
    private final ReportGeneratorService reportGeneratorService;
    private final AdminService adminService;
    private final CompanyAccessGuard companyAccessGuard;

    private static final Logger LOGGER = LoggerFactory.getLogger(Report.class);

//    @PostMapping("/{companyId}")
//    public ResponseEntity<?> getReports(
//            @PathVariable String companyId,
//            @RequestBody ReportPaginationRequest request
//    ) {
//        int page = request.getPage();
//        int size = request.getSize();
//        Map<String, Object> response = reportPersistenceService.getPayRollReports(companyId, page, size);
//        return new ResponseEntity<>(response, HttpStatus.OK);
//    }

    @GetMapping("/{companyId}/status/{status}")
    public List<ReportResponse> getReportsByStatus(@PathVariable String companyId, @PathVariable String status) {
        companyAccessGuard.requireCompanyAccess(companyId);
        return reportPersistenceService.getPayRollReportsByStatus(companyId, status);
    }

    @PostMapping("/by-reportId")
    public ReportResponse getReport( @RequestBody RetrieveSummaryElementRequest request) {
        if (request.getCompanyId() != null) {
            companyAccessGuard.requireCompanyAccess(request.getCompanyId());
        }
        ReportResponse report = reportPersistenceService.getPayRollReport(UUID.fromString(request.getReportId()));
        requireReportCompanyAccess(report);
        return report;
    }

    @PostMapping("/report-summary-by-filter")
    public PaginatedReportSummaryResponse getReportSummaryByFilter(@RequestBody ReportFilterRequest request) {
        companyAccessGuard.requireCompanyAccess(request.getCompanyId());
        return reportPersistenceService.getReportSummaryByFilter(request);
    }

    @GetMapping("/{companyId}/{employeeId}")
    public ResponseEntity<?> getReportByEmployeeID(
            @PathVariable String companyId,
            @PathVariable String employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size
    ) {
        companyAccessGuard.requireCompanyAccess(companyId);
        Map<String, Object> response =  reportPersistenceService.getReportByEmployeeID(companyId,  employeeId, page, size);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/filterReports")
    public ResponseEntity<?> getReportByFilter(
            @RequestBody EmployeeFilterRequest employeeFilterRequest,
            @RequestHeader("Authorization") String authorizationHeader) {

        companyAccessGuard.requireCompanyAccess(employeeFilterRequest.getCompanyID());
        PaginatedSelectedEmployeeField selectedEmployeeField;

        // we need to always call admin so that we can pull the hire date and employeeCode
        selectedEmployeeField = adminService.getEmployeeIdListForFilter(employeeFilterRequest, authorizationHeader);

        assert selectedEmployeeField != null;

        List<String> filteredList = selectedEmployeeField.getSelectedEmployeeFields().stream().map(SelectedEmployeeField::getEmployeeID).toList();

        Map<String, Object> response =  reportPersistenceService.getReportByEmployeeIDList(employeeFilterRequest.getCompanyID(),
                filteredList, employeeFilterRequest.getReportId(), selectedEmployeeField,
                employeeFilterRequest.getPage(), employeeFilterRequest.getSize());

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/details/report")
    public ResponseEntity<?> getDetailsReportByFilter(
            @RequestBody EmployeeFilterRequest employeeFilterRequest) {

        companyAccessGuard.requireCompanyAccess(employeeFilterRequest.getCompanyID());
        Map<String, Object> response =  reportPersistenceService.getReportByEmployeeIDList(employeeFilterRequest.getCompanyID(),
                employeeFilterRequest.getEmployeeIds(), employeeFilterRequest.getReportId(), null,
                employeeFilterRequest.getPage(), employeeFilterRequest.getSize());

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/analytics/{companyId}")
    public List<ReportAnalytics> getAnalyticsReports(@PathVariable String companyId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "12") int size
    ) {
        companyAccessGuard.requireCompanyAccess(companyId);
        return reportPersistenceService.getReportAnalytics(companyId, page, size);
    }

    @GetMapping("/get-by-start-date/{companyId}/{startDate}")
    public ReportResponse getReport(@PathVariable String startDate, @PathVariable String companyId) {
        companyAccessGuard.requireCompanyAccess(companyId);
        return reportPersistenceService.getPayRollReport(startDate, companyId);
    }

    @PostMapping("/get-by-start-date-and-category")
    public Map<String, Object> getReportByType(
            @RequestBody ReportByTypeRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        companyAccessGuard.requireCompanyAccess(request.getCompanyId());
        return reportPersistenceService.getPayRollReportByType(request, page, size);
    }

    @PostMapping("/get-by-start-date-and-employeeId")
    public Map<String, Object> getPayRollReportDetailByType(
            @RequestBody ReportByTypeRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        companyAccessGuard.requireCompanyAccess(request.getCompanyId());
        return reportPersistenceService.getPayRollReportDetailByType(request, page, size);
    }

//    @PutMapping("/approve")
//    public boolean approveReport(@RequestBody UpdateReportRequest request) {
//        PayrollReportSummary payrollReport = reportPersistenceService.approveReport(request);
//        return true;
//    }

    @PutMapping("/update-report-status")
    public void updateStatus(@RequestBody UpdatePayrollStatusRequest request) {
        companyAccessGuard.requireCompanyAccess(request.getCompanyId());
        try {
            LOGGER.info("Updating report status to {}", request.getStatus());
            reportPersistenceService.updateReportStatus(request);
        } catch (IllegalArgumentException e) {
            throw new PayrollValidationException("Invalid payroll status: " + request.getStatus());
        }
    }

//    @PutMapping("/cancel")
//    public boolean deleteReport(
//            @RequestBody UpdateReportRequest request,
//            @RequestHeader("Authorization") String token
//    ) {
//        return reportPersistenceService.deleteReport(request, token);
//    }

//    @PostMapping("/complete")
//    public CompletePayrollResponse completeReport(@RequestBody CompletePayrollRequest request) {
//        return reportPersistenceService.completeReport(request);
//    }

    @GetMapping("/paymentDetails")
    public ResponseEntity<?> getPaymentDetails(
            @RequestParam() String id,
            @RequestParam() String companyId,
            @RequestParam(defaultValue = "") String fullName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size) {
        companyAccessGuard.requireCompanyAccess(companyId);
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
        companyAccessGuard.requireCompanyAccess(companyId);
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
        companyAccessGuard.requireCompanyAccess(companyId);
        ReportResponse response = reportPersistenceService
                .getPaymentDetailsByEmployee(employeeId, startDate, companyId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/ytdReport")
    public ResponseEntity<?> getYtdReport(
            @RequestParam() String employeeId,
            @RequestParam() String companyId
    ) {
        companyAccessGuard.requireCompanyAccess(companyId);
        YTDReport response = reportPersistenceService.getYTDReport(employeeId, companyId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/ytdReport-by-employeeIds")
    public ResponseEntity<?> getYtdReportsByEmployeeIds(
            @RequestBody() YtdRequest request
    ) {
        companyAccessGuard.requireCompanyAccess(request.getCompanyId());
        List<YTDReport> response = reportPersistenceService.getYTDReports(request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

//    @PostMapping("/download-report")
//    public ResponseEntity<byte[]> uploadReport(@RequestBody ReportRequestPayload request) throws IOException {
//        byte[] excelFile = reportGeneratorService.generateReport(request);
//
//        // 🔹 Store file locally
////        Path folder = Paths.get("./exports");  // relative folder inside Spring Boot run dir
////        if (!Files.exists(folder)) {
////            Files.createDirectories(folder);
////        }
////        Path filePath = folder.resolve("report-detail.xlsx");
////        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
////            fos.write(excelFile);
////        }
//
//        String fileName = "payroll-report." +
//                (request.getDocType().equalsIgnoreCase("pdf") ? "pdf" : "xlsx");
//
//        HttpHeaders headers = new HttpHeaders();
//
//        headers.setContentType(
//                request.getDocType().equalsIgnoreCase("pdf") ?
//                        MediaType.APPLICATION_PDF :
//                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
//        );
//
//        headers.setContentDisposition(
//                ContentDisposition.attachment()
//                        .filename(fileName)
//                        .build()
//        );
//
//        headers.setCacheControl(CacheControl.noCache().getHeaderValue());
//
//        return new ResponseEntity<>(excelFile, headers, HttpStatus.OK);
//    }


    @PostMapping("/download-report")
    public ResponseEntity<byte[]> uploadReport(@RequestBody ReportRequestPayload payload,
                                               @RequestHeader("Authorization") String authorizationHeader
    ) throws IOException {

        companyAccessGuard.requireCompanyAccess(payload.getCompanyID());
        byte[] excelFile = reportGeneratorService.generateReport(payload, authorizationHeader);
               // 🔹 Store file locally
//        Path folder = Paths.get("./exports");  // relative folder inside Spring Boot run dir
//        if (!Files.exists(folder)) {
//            Files.createDirectories(folder);
//        }
//        Path filePath = folder.resolve("report-detail.xlsx");
//        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
//            fos.write(excelFile);
//        }

        String fileName;

        if(payload.getDateRange() == null) {
            fileName = "payroll-report." +
                    (payload.getDocType().equalsIgnoreCase("pdf") ? "pdf" : "xlsx");
        } else {
            fileName = "payroll-report" + payload.getDateRange().getStart() + "." +
                    (payload.getDocType().equalsIgnoreCase("pdf") ? "pdf" : "xlsx");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                payload.getDocType().equalsIgnoreCase("pdf") ?
                        MediaType.APPLICATION_PDF :
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        );

        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(fileName)
                        .build()
        );

        headers.setCacheControl(CacheControl.noCache().getHeaderValue());

        return new ResponseEntity<>(excelFile, headers, HttpStatus.OK);
 }

    @PostMapping("/retrieve-payment-element")
    public List<Map<String, Object>> getPaymentElement(@RequestBody RetrievePaymentElementPayload retrievePaymentElementPayload){
        return reportGeneratorService.retrievePaymentElementFromReport(retrievePaymentElementPayload);
    }

    @GetMapping("/payment-header-options/company-id/{companyID}/report-id/{reportId}")
    public Set<String> getAllHeadersForReport(@PathVariable String companyID, @PathVariable String reportId) {
        companyAccessGuard.requireCompanyAccess(companyID);
        return reportGeneratorService.getHeadersForReport(companyID, reportId);
    }

    @PostMapping("/total-netpay-by-report-id")
    public Map<String, Object> getTotalNetPayByReportId(@RequestBody RetrieveSummaryElementRequest request){
        if (request.getCompanyId() != null) {
            companyAccessGuard.requireCompanyAccess(request.getCompanyId());
        }
        return reportGeneratorService.extractDataFromSummary(request);
    }

    @PostMapping("/variance-details")
    public ResponseEntity<?> getVarianceDetails(
            @RequestBody EmployeeFilterRequest employeeFilterRequest,
            @RequestHeader("Authorization") String authorizationHeader) {
        companyAccessGuard.requireCompanyAccess(employeeFilterRequest.getCompanyID());
        employeeFilterRequest.setSize(5000);
        PaginatedSelectedEmployeeField selectedEmployeeField = adminService.getEmployeeIdListForFilter(employeeFilterRequest, authorizationHeader);

        List<String> filteredList = selectedEmployeeField.getSelectedEmployeeFields().stream().map(SelectedEmployeeField::getEmployeeID).toList();

        ConcurrentHashMap<String, Set<SummaryDetail>> response =
                reportPersistenceService.getSummaryVarianceDetails(
                        employeeFilterRequest.getReportId()
                );
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/variance-details-by-employeeId")
    public ResponseEntity<?> getVarianceDetailsByEmployeeId(
            @RequestBody EmployeeFilterRequest employeeFilterRequest,
            @RequestHeader("Authorization") String authorizationHeader) {
        companyAccessGuard.requireCompanyAccess(employeeFilterRequest.getCompanyID());
//        PaginatedSelectedEmployeeField selectedEmployeeField = adminService.getEmployeeIdListForFilter(employeeFilterRequest, authorizationHeader);

//        List<String> filteredList = selectedEmployeeField.getSelectedEmployeeFields().stream().map(SelectedEmployeeField::getEmployeeID).toList();

        Map<String, Object> response =
                reportPersistenceService.getSummaryVarianceDetailsByEmployee(
                        employeeFilterRequest.getReportId(),
                        employeeFilterRequest.getPage(),
                        employeeFilterRequest.getSize()
//                        filteredList,
//                        employeeFilterRequest.getHeader()
                );
        return new ResponseEntity<>(response, HttpStatus.OK);
    }


    @PostMapping("/variance-details-customized")
    public ResponseEntity<?> getVarianceDetailsCustomized(
            @RequestBody CustomizedVarianceRequest request) {
        // company scoped via report lookup inside service; employeeIds-only request still needs auth company when provided
        Map<String, Map<String, BigDecimal>> response = reportPersistenceService.getSummaryVarianceDetails(request.getReportId(), request.getEmployeeIds());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }


    private void requireReportCompanyAccess(ReportResponse report) {
        if (report != null && report.getCompanyId() != null) {
            companyAccessGuard.requireCompanyAccess(report.getCompanyId());
        }
    }

}



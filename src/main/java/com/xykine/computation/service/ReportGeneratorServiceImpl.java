package com.xykine.computation.service;

import com.xykine.computation.dto.EmployeeDetail;
import com.xykine.computation.request.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.repo.PayrollReportDetailRepo;
import com.xykine.computation.repo.PayrollReportSummaryRepo;

import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.utils.ReportUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.springframework.stereotype.Service;
import org.xykine.payroll.model.MapKeys;
import org.xykine.payroll.model.PaymentInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class ReportGeneratorServiceImpl implements ReportGeneratorService {

    private final PayrollReportDetailRepo payrollReportDetailRepo;
    private final PayrollReportSummaryRepo payrollReportSummaryRepo;
    private final ExcelUploadService excelUploadService;
    private final AdminService adminService;

    @Override
    public byte[] generateReport(ReportRequestPayload reportRequestPayload, String token) throws IOException {

        if (reportRequestPayload.getEntityType() == null) {
            throw new RuntimeException("Report type is mandatory");
        }

        if (reportRequestPayload.getCompanyID() == null) {
            throw new RuntimeException("CompanyId is mandatory");
        }

        EmployeeFilterRequest employeeFilterRequest = new EmployeeFilterRequest();
        employeeFilterRequest.setCompanyID(reportRequestPayload.getCompanyID());

        Map<String, EmployeeDetail> employeeDetailMap = adminService.getEmployeesDetail(employeeFilterRequest, token);

        if (reportRequestPayload.isDefaultHeaders()) {
        List<String> defaultHeaders = new ArrayList<>();
            defaultHeaders.add("Gross Pay");
            defaultHeaders.add("Housing Allowance");
            defaultHeaders.add("Transport Allowance");
            defaultHeaders.add("Utility");
            defaultHeaders.add("Entertainment");
            defaultHeaders.add("Medical");
            defaultHeaders.add("PERSONAL OUTFIT");
            defaultHeaders.add("Leave");
            defaultHeaders.add("Training");
            defaultHeaders.add("Monthly Performance Bonus");
            defaultHeaders.add("overtime");
            defaultHeaders.add("other variable");
            defaultHeaders.add("other allowance");
            defaultHeaders.add("other wage types");
            defaultHeaders.add("CHARGEABLE INCOME");
            defaultHeaders.add("other deduction");
            defaultHeaders.add("Loan");   // **
            defaultHeaders.add("Monthly Paye");
            defaultHeaders.add("National Housing Fund");
            defaultHeaders.add("Employee Pension Contribution");
            defaultHeaders.add("Voluntary Pension Contribution");
            defaultHeaders.add("Employer Pension Contribution");
            defaultHeaders.add("Net Pay");

            reportRequestPayload.setHeaders(defaultHeaders);
        }

        List<?> source; // raw entities before transform

        // Decide source based on type + flags
        switch (reportRequestPayload.getEntityType()) {
            case "details" -> {

                List<String> headers = reportRequestPayload.getHeaders();
                if (reportRequestPayload.isAll()) {
                    source = payrollReportDetailRepo.findByCompanyId(reportRequestPayload.getCompanyID());
                } else if (!reportRequestPayload.getIds().isEmpty()) {
                    source = payrollReportDetailRepo.findPayrollReportDetailByEmployeeIdInAndCompanyId(reportRequestPayload.getIds(), reportRequestPayload.getCompanyID());
                } else {
                    source = List.of();
                }
            }
            case "summary" -> {
                if (reportRequestPayload.isAll()) {
                    source = payrollReportSummaryRepo.findPayrollReportSummaryByCompanyId(reportRequestPayload.getCompanyID());
                } else if (!reportRequestPayload.getIds().isEmpty()) {
                    source = payrollReportSummaryRepo.findPayrollReportSummaryByIdInAndCompanyId(reportRequestPayload.getIds(), reportRequestPayload.getCompanyID());
                } else {
                    source = List.of();
                }
            }
            default -> throw new RuntimeException("Invalid report type: " + reportRequestPayload.getEntityType());
        }
        AtomicBoolean isDetail = new AtomicBoolean(false);
        // Transform, filter, and map into data rows
        List<Map<String, Object>> dataRows = source.stream()
                .filter(Objects::nonNull)
                .map(obj -> {
                    if (obj instanceof PayrollReportDetail detail) {
                        isDetail.set(true);
                        return ReportUtils.transform(detail); // returns ReportResponse
                    } else if (obj instanceof PayrollReportSummary summary) {
                        return ReportUtils.transform(summary); // returns ReportResponse
                    } else {
                        throw new IllegalArgumentException("Unsupported type: " + obj.getClass());
                    }
                })
                .filter(detail -> filterByDates(detail, reportRequestPayload))
                .map(detail -> extractDetail(
                        detail.getDetail().getReport(),
                        reportRequestPayload.getHeaders(),
                        isDetail.get(),
                        employeeDetailMap
                ))
                .toList();

        if (dataRows.isEmpty()) {
            throw new RuntimeException("No data found for selected employees/reports");
        }

        List<String> headers = new ArrayList<>(dataRows.get(0).keySet());
        String fileName = reportRequestPayload.getCompanyID() +"_" + reportRequestPayload.getEntityType() + "_" + reportRequestPayload.getDateRange().getFromDate() + "_" + reportRequestPayload.getDateRange().getEndDate() + ".xlsx";

        return generateExcel(headers, dataRows, fileName);
    }

    @Override
    public Set<String> getHeadersForReport(String companyId, String reportId) {
        Pageable paging = PageRequest.of(0, 1);

        return payrollReportDetailRepo
                .findPayrollReportDetailBySummaryIdAndCompanyId(reportId, companyId, paging).stream()
                .filter(Objects::nonNull)
                .findFirst()
                .map(ReportUtils::transform)
                .map(detail -> extractRawDetail(detail.getDetail().getReport()))
                .map(Map::keySet)
                .orElse(Collections.emptySet());
    }

    @Override
    public List<Map<String, Object>> retrievePaymentElementFromReport(RetrievePaymentElementPayload retrievePaymentElementPayload) {
       return payrollReportDetailRepo
                .findPayrollReportDetailBySummaryId(retrievePaymentElementPayload.getReportId()).stream()
                .filter(Objects::nonNull)
                .map(ReportUtils::transform)
                .map(detail -> extractDetail(detail.getDetail().getReport(), retrievePaymentElementPayload.getSelectedHeader(), true, null))
                .toList();
    }

    @Override
    public Map<String, Object> extractDataFromSummary(RetrieveSummaryElementRequest request) {
        Map<String, Object> result = new HashMap<>();

        try {
                payrollReportSummaryRepo
                    .findPayrollReportSummaryByIdAndCompanyId(UUID.fromString(request.getReportId()), request.getCompanyId())
                    .ifPresentOrElse(payrollReportSummary -> {
                        ReportResponse reportResponse = ReportUtils.transform(payrollReportSummary);
                        result.put("Total Number of Recipients", payrollReportDetailRepo.countBySummaryId(request.getReportId()));
                        result.put(MapKeys.TOTAL_NET_PAY,
                                reportResponse.getSummary().getSummary().getOrDefault(MapKeys.TOTAL_NET_PAY, BigDecimal.valueOf(0)));
                    }, () -> {
                        result.put("Total Number of Recipients", 0);
                        result.put(MapKeys.TOTAL_NET_PAY, 0);
                    });

            return result;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException("Unable to get summary");
        }
    }

    private boolean filterByDates(ReportResponse detail, ReportRequestPayload payload) {
        DateRange dateRange = payload.getDateRange();
        if (dateRange == null) {
            throw new IllegalArgumentException("Date range cannot be null");
        }
        if (detail.getStartDate() == null) {
            return false;
        }

        LocalDate startDateInstance;
        try {
            startDateInstance = LocalDate.parse(
                    detail.getStartDate(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd")
            );
        } catch (DateTimeParseException e) {
            return false;
        }
        return !startDateInstance.isBefore(dateRange.getFromDate())
                && !startDateInstance.isAfter(dateRange.getEndDate());
    }

    private Map<String, Object> extractDetail(PaymentInfo paymentInfo, List<String> selectedReports, boolean isDetail, Map<String, EmployeeDetail> employeeDetailMap) {
        Map<String, Object> raw = extractRawDetail(paymentInfo);
        Map<String, Object> result = new LinkedHashMap<>();

        if (isDetail) {
            result.put("EMP ID", employeeDetailMap != null ? employeeDetailMap.get(paymentInfo.getEmployeeID()).getMappedId() : " ");
            result.put("EMPLOYEE NAME", paymentInfo.getFullName());
            result.put("HIRE DATE", employeeDetailMap != null ? employeeDetailMap.get(paymentInfo.getEmployeeID()).getHireDate() : " ");
            result.put("EXIT DATE", employeeDetailMap != null ? employeeDetailMap.get(paymentInfo.getEmployeeID()).getExitDate() : " ");
            result.put("ROLE", employeeDetailMap != null ? employeeDetailMap.get(paymentInfo.getEmployeeID()).getRole() : " ");
        }
        Map<String, Object> finalResult = new HashMap<>(result);
        selectedReports.forEach(key -> {
            Object value = raw.getOrDefault(key, " ");
            finalResult.put(key, value);
        });
        return swapKey(finalResult);
    }

    private Map<String, Object> extractRawDetail(PaymentInfo paymentInfo) {
        Map<String, Object> raw = new HashMap<>();
        raw.put("EmployeeId", paymentInfo.getEmployeeID());
        raw.put("EmployeeName", paymentInfo.getFullName());
        raw.put("StartDate", paymentInfo.getStartDate());
        raw.put("EndDate", paymentInfo.getEndDate());
        raw.put("PayrollType", paymentInfo.isOffCycle()? "OffCycle" : "Regular");
        raw.put(MapKeys.NET_PAY, paymentInfo.getNetPay());

        List<Map<String, BigDecimal>> components = Arrays.asList(

                paymentInfo.getDeduction(),
                paymentInfo.getTaxRelief(),
                paymentInfo.getGrossPay(),
                paymentInfo.getEarning(),
                paymentInfo.getOthers(),
                paymentInfo.getPension()
        );

        components.stream()
                .filter(Objects::nonNull) // Ensure the component is not null
                .forEach(raw::putAll);

        return raw;
    }

    private byte[] generateExcel(List<String> headers, List<Map<String, Object>> dataRows, String fileName) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Report");

            // Header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }

            // Data rows
            for (int i = 0; i < dataRows.size(); i++) {
                Row row = sheet.createRow(i + 1);
                Map<String, Object> rowData = dataRows.get(i);
                for (int j = 0; j < headers.size(); j++) {
                    Object value = rowData.get(headers.get(j));
                    Cell cell = row.createCell(j);
                    if (value instanceof Number) {
                        cell.setCellValue(((Number) value).doubleValue());
                    } else if (value != null) {
                        cell.setCellValue(value.toString());
                    } else {
                        cell.setBlank();
                    }
                }
            }

            // 🔹 Auto-size all columns, but enforce a minimum width (15 chars)
            int totalCols = 2 + (dataRows.size() * 2);
            for (int i = 0; i < totalCols; i++) {
                sheet.autoSizeColumn(i);
                int currentWidth = sheet.getColumnWidth(i);
                int minWidth = 25 * 256; // 15 characters
                if (currentWidth < minWidth) {
                    sheet.setColumnWidth(i, minWidth);
                }
            }

            // 🔹 Freeze header row
            sheet.createFreezePane(0, 1);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private Map<String, Object> swapKey(Map<String, Object> result) {
        Map<String, Object> renamedPayroll = new HashMap<>();
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String newKey;

            switch (key) {
                case "Gross Pay":
                    newKey = "GROSS PAY.";
                    break;
                case "Basic Salary":
                    newKey = "BASIC SALARY.";
                    break;
                case "Housing Allowance":
                    newKey = "HOUSING,";
                    break;
                case "Transport Allowance":
                    newKey = "TRANSPORT,";
                    break;
                case "Utility":
                    newKey = "UTILITY,";
                    break;
                case "Entertainment":
                    newKey = "ENTERTAINMENT,";
                    break;
                case "Medical":
                    newKey = "MEDICAL,";
                    break;
                case "PERSONAL OUTFIT":
                    newKey = "PERSONAL OUTFIT,";
                    break;
                case "Leave":
                    newKey = "LEAVE,";
                    break;
                case "Training":
                    newKey = "TRAINING";
                    break;
                case "Monthly Performance Bonus":
                    newKey = "PERFORMANCE BONUS";
                    break;
                case "overtime":
                    newKey = "OVERTIME";
                    break;
                case "other variable":
                    newKey = "OTHER VARIABLE";
                    break;
                case "other allowance":
                    newKey = "OTHER ALLOWANCE";
                    break;
                case "other wage types":
                    newKey = "OTHER WAGE TYPES";
                    break;
                case "CHARGEABLE INCOME":
                    newKey = "TAXABLE INCOME";
                    break;
                case "other deduction":
                    newKey = "OTHER DEDUCTION";
                    break;
                case "Loan.":
                    newKey = "LOAN DEDUCTION";
                    break;
                case "Monthly Paye":
                    newKey = "PAYE";
                    break;
                case "National Housing Fund":
                    newKey = "NHF";
                    break;
                case "Employee Pension Contribution":
                    newKey = "EMPLOYEE PENSION";
                    break;
                case "Voluntary Pension Contribution":
                    newKey = "VOLUNTARY PENSION CONTRIBUTION";
                    break;
                case "Employer Pension Contribution":
                    newKey = "EMPLOYER PENSION";
                    break;
                case "Net Pay":
                    newKey = "NETPAY";
                    break;
                default:
                    newKey = key;
            }
            renamedPayroll.put(newKey, value);
        }
        return renamedPayroll;
    }
}

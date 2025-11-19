package com.xykine.computation.service;

import com.xykine.computation.dto.EmployeeDetail;
import com.xykine.computation.request.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.usermodel.*;

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

    private static final List<String> TEMPLATE_HEADERS = List.of(
            "EMP ID",
            "EMPLOYEE NAME",
            "HIRE DATE",
            "EXIT DATE",
            "ROLE",
            "GROSS PAY",
            "GROSS SALARY",
            "BASIC SALARY",
            "HOUSING",
            "TRANSPORT",
            "UTILITY",
            "ENTERTAINMENT",
            "MEDICAL",
            "PERSONAL OUTFIT",
            "LEAVE",
            "TRAINING",
            "PERFORMANCE BONUS",
            "OVERTIME",
            "OTHER VARIABLE",
            "OTHER ALLOWANCE",
            "OTHER WAGE TYPES",
            "TAXABLE INCOME",
            "OTHER DEDUCTION",
            "LOAN DEDUCTION",
            "PAYE",
            "NHF",
            "EMPLOYEE PENSION",
            "VOLUNTARY PENSION CONTRIBUTION",
            "EMPLOYER PENSION",
            "NETPAY"
    );


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
            defaultHeaders.add("Basic Salary");
            defaultHeaders.add("Housing");
            defaultHeaders.add("Transport");
            defaultHeaders.add("Utility");
            defaultHeaders.add("Entertainment");
            defaultHeaders.add("Medical");
            defaultHeaders.add("Personal Outfit");
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
        int i = 0;
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
                        employeeDetailMap,
                        detail.getReportId()
                ))
                .toList();
//
//        if (dataRows.isEmpty()) {
//            throw new RuntimeException("No data found for selected employees/reports");
//        }
//
//        List<String> headers = new ArrayList<>(dataRows.get(0).keySet());
//        String fileName = reportRequestPayload.getCompanyID() +"_" + reportRequestPayload.getEntityType() + "_" + reportRequestPayload.getDateRange().getFromDate() + "_" + reportRequestPayload.getDateRange().getEndDate() + ".xlsx";
//
//        return generateExcel(headers, dataRows, fileName);

        // After dataRows is built:
        if (dataRows.isEmpty()) {
            throw new RuntimeException("No data found for selected employees/reports");
        }

        // Make sure all template headers exist as keys (fill blanks for missing)
        // and enforce the exact template order
        List<String> headers = new ArrayList<>(TEMPLATE_HEADERS);
        List<Map<String, Object>> normalizedRows = new ArrayList<>(dataRows.size());

        for (Map<String, Object> row : dataRows) {
            Map<String, Object> norm = new LinkedHashMap<>();
            for (String h : TEMPLATE_HEADERS) {
                norm.put(h, row.getOrDefault(h, " "));
            }
            normalizedRows.add(norm);
        }

        // file name stays same
        String fileName = reportRequestPayload.getCompanyID() + "_" +
                reportRequestPayload.getEntityType() + "_" +
                reportRequestPayload.getDateRange().getFromDate() + "_" +
                reportRequestPayload.getDateRange().getEndDate() + ".xlsx";

        return generateExcel(headers, normalizedRows, fileName);

    }

    @Override
    public Set<String> getHeadersForReport(String companyId, String reportId) {
        Pageable paging = PageRequest.of(0, 1);

        return payrollReportDetailRepo
                .findPayrollReportDetailBySummaryIdAndCompanyId(reportId, companyId, paging).stream()
                .filter(Objects::nonNull)
                .findFirst()
                .map(ReportUtils::transform)
                .map(detail -> extractRawDetail(detail.getDetail().getReport(), detail.getReportId()))
                .map(Map::keySet)
                .orElse(Collections.emptySet());
    }

    @Override
    public List<Map<String, Object>> retrievePaymentElementFromReport(RetrievePaymentElementPayload retrievePaymentElementPayload) {
        return payrollReportDetailRepo
                .findPayrollReportDetailBySummaryId(retrievePaymentElementPayload.getReportId()).stream()
                .filter(Objects::nonNull)
                .map(ReportUtils::transform)
                .map(detail -> extractDetailBefore(detail.getDetail().getReport(), retrievePaymentElementPayload.getSelectedHeader(), true, detail.getReportId()))
                    //TODO fix later
//                .map(detail -> extractDetail(detail.getDetail().getReport(), retrievePaymentElementPayload.getSelectedHeader(), true, null))
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


    //TODO debug and merge
    private Map<String, Object> extractDetailBefore(PaymentInfo paymentInfo, List<String> selectedReports, boolean isDetail, String reportDetailId) {
        Map<String, Object> raw = extractRawDetail(paymentInfo, reportDetailId);
        Map<String, Object> result = new LinkedHashMap<>();

        if (isDetail) {
            result.put("FULL NAME", paymentInfo.getFullName());
        }
        selectedReports.forEach(key -> {
            if (raw.containsKey(key)) {
                result.put(key, raw.get(key));
            }
        });

        return result;
    }



    private Map<String, Object> extractDetail(PaymentInfo paymentInfo, List<String> selectedReports, boolean isDetail, Map<String, EmployeeDetail> employeeDetailMap, String reportDetailId) {
        Map<String, Object> raw = extractRawDetail(paymentInfo, reportDetailId);
        Map<String, Object> result = new LinkedHashMap<>();
        String employeeId = paymentInfo.getEmployeeID();

        final EmployeeDetail employeeDetail = (employeeDetailMap != null) ? employeeDetailMap.get(employeeId) : null;

        if (isDetail && employeeDetail != null) {
            String exitDate = employeeDetail.getExitDate().equals(employeeDetail.getHireDate()) ? "" : employeeDetail.getExitDate();

            result.put("EMP ID", employeeDetail.getMappedId());
            result.put("EMPLOYEE NAME", paymentInfo.getFullName());
            result.put("HIRE DATE", employeeDetail.getHireDate());
            result.put("EXIT DATE", exitDate);
            result.put("ROLE", employeeDetail.getRole());
        }

        Map<String, Object> finalResult = new HashMap<>(result);
        selectedReports.forEach(key -> {
            Object value = raw.getOrDefault(key, " ");
            finalResult.put(key, value);
        });
        finalResult.put("GROSS SALARY", deriveGrossSalary(paymentInfo.getGrossPay()));

        //add grossSalary

        return swapKey(finalResult);
    }

    //Gross Salary is the summation of gross pay
    private BigDecimal deriveGrossSalary(Map<String, BigDecimal> grossPay) {
        if (grossPay == null || grossPay.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return grossPay.entrySet().stream()
                //TODO let use enum for all out keys
                //rework this
                .filter(e ->
                        !"Gross Pay".equalsIgnoreCase(e.getKey()) &&
                        !"Monthly Performance Bonus".equalsIgnoreCase(e.getKey()))   // exclude Gross Pay
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)                                   // avoid null values
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, Object> extractRawDetail(PaymentInfo paymentInfo, String reportDetailId) {
        Map<String, Object> raw = new HashMap<>();
        raw.put("EmployeeId", paymentInfo.getEmployeeID());
        raw.put("DetailId", reportDetailId);
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
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet("Report");

            // === Colors (ARGB). Swap to your exact template codes if you want a perfect match ===
            final String HEADER_BLUE  = "FF1F4E79";  // dark blue; white text recommended
            final String BANNER_CYAN  = "FF00D7EF";  // cyan/aqua for the "GROSS SALARY" band

            // --- Styles ---
            // Header (blue) style
            XSSFCellStyle headerStyle = workbook.createCellStyle();
            XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setFillForegroundColor(argb(workbook, HEADER_BLUE));
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Banner (cyan) style
            XSSFCellStyle bannerStyle = workbook.createCellStyle();
            XSSFFont bannerFont = workbook.createFont();
            bannerFont.setBold(true);
            bannerFont.setColor(IndexedColors.BLACK.getIndex());
            bannerStyle.setFont(bannerFont);
            bannerStyle.setAlignment(HorizontalAlignment.CENTER);
            bannerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            bannerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            bannerStyle.setFillForegroundColor(argb(workbook, BANNER_CYAN));
            bannerStyle.setBorderBottom(BorderStyle.THIN);
            bannerStyle.setBorderTop(BorderStyle.THIN);
            bannerStyle.setBorderLeft(BorderStyle.THIN);
            bannerStyle.setBorderRight(BorderStyle.THIN);

            // --- Row 0: (we’ll leave empty so the cyan band sits visually at row 1) ---
            sheet.createRow(0);

            // --- Row 1: GROSS SALARY band over G..O (GROSS PAY .. TRAINING) ---
            int startGross = headers.indexOf("GROSS PAY");
            int endGross   = headers.indexOf("TRAINING"); // per your screenshot & template
            if (startGross >= 0 && endGross >= startGross) {
                Row bandRow = sheet.createRow(1);
                for (int j = startGross; j <= endGross; j++) {
                    Cell c = bandRow.createCell(j);
                    c.setCellStyle(bannerStyle);
                }
                // Merge the region and set the text in the first cell
                CellRangeAddress region = new CellRangeAddress(1, 1, startGross, endGross);
                sheet.addMergedRegion(region);
                Cell first = bandRow.getCell(startGross);
                first.setCellValue("GROSS SALARY");
                first.setCellStyle(bannerStyle);

                // Draw borders around the merged region
                RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
                RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
                RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);
                RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
            }

            // --- Row 2: Headers (all blue) ---
            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(headerStyle);
            }

            XSSFCellStyle numberStyle = workbook.createCellStyle();
            XSSFDataFormat format = workbook.createDataFormat();
            numberStyle.setDataFormat(format.getFormat("#,##0.00"));

            // --- Rows 3+: Data ---
            for (int i = 0; i < dataRows.size(); i++) {
                Row row = sheet.createRow(i + 3); // adjust if header rows differ
                Map<String, Object> rowData = dataRows.get(i);

                for (int j = 0; j < headers.size(); j++) {
                    Object value = rowData.get(headers.get(j));
                    Cell cell = row.createCell(j);

                    if (value instanceof Number) {
                        cell.setCellValue(((Number) value).doubleValue());
                        cell.setCellStyle(numberStyle); // ✅ Apply comma format
                    } else if (value != null) {
                        cell.setCellValue(value.toString());
                    } else {
                        cell.setBlank();
                    }
                }
            }


            // Autosize + minimum width
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
                int currentWidth = sheet.getColumnWidth(i);
                int minWidth = 22 * 256; // ~22 chars
                if (currentWidth < minWidth) sheet.setColumnWidth(i, minWidth);
            }

            // Freeze top 3 rows (empty row 0, cyan band row 1, and blue header row 2)
            sheet.createFreezePane(0, 3);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
//    private byte[] generateExcel(List<String> headers, List<Map<String, Object>> dataRows, String fileName) throws IOException {
//        try (Workbook workbook = new XSSFWorkbook();
//             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
//
//            Sheet sheet = workbook.createSheet("Report");
//
//            // Header row
//            Row headerRow = sheet.createRow(0);
//            for (int i = 0; i < headers.size(); i++) {
//                headerRow.createCell(i).setCellValue(headers.get(i));
//            }
//
//            // Data rows
//            for (int i = 0; i < dataRows.size(); i++) {
//                Row row = sheet.createRow(i + 1);
//                Map<String, Object> rowData = dataRows.get(i);
//                for (int j = 0; j < headers.size(); j++) {
//                    Object value = rowData.get(headers.get(j));
//                    Cell cell = row.createCell(j);
//                    if (value instanceof Number) {
//                        cell.setCellValue(((Number) value).doubleValue());
//                    } else if (value != null) {
//                        cell.setCellValue(value.toString());
//                    } else {
//                        cell.setBlank();
//                    }
//                }
//            }
//
//            // 🔹 Auto-size all columns, but enforce a minimum width (15 chars)
//            int totalCols = 2 + (dataRows.size() * 2);
//            for (int i = 0; i < totalCols; i++) {
//                sheet.autoSizeColumn(i);
//                int currentWidth = sheet.getColumnWidth(i);
//                int minWidth = 25 * 256; // 15 characters
//                if (currentWidth < minWidth) {
//                    sheet.setColumnWidth(i, minWidth);
//                }
//            }
//
//            // 🔹 Freeze header row
//            sheet.createFreezePane(0, 1);
//
//            workbook.write(outputStream);
//            return outputStream.toByteArray();
//        }
//    }

    private Map<String, Object> swapKey(Map<String, Object> result) {
        Map<String, Object> renamed = new HashMap<>();
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String newKey;

            switch (key) {
                case "Gross Pay": newKey = "GROSS PAY"; break;
                case "Basic Salary": newKey = "BASIC SALARY"; break;
                case "GROSS SALARY": newKey = "GROSS SALARY"; break;
                case "Housing": newKey = "HOUSING"; break;
                case "Transport": newKey = "TRANSPORT"; break;
                case "Utility": newKey = "UTILITY"; break;
                case "Entertainment": newKey = "ENTERTAINMENT"; break;
                case "Medical": newKey = "MEDICAL"; break;
                case "Personal Outfit": newKey = "PERSONAL OUTFIT"; break;
                case "Leave": newKey = "LEAVE"; break;
                case "Training": newKey = "TRAINING"; break;
                case "Monthly Performance Bonus": newKey = "PERFORMANCE BONUS"; break;
                case "overtime": newKey = "OVERTIME"; break;
                case "other variable": newKey = "OTHER VARIABLE"; break;
                case "other allowance": newKey = "OTHER ALLOWANCE"; break;
                case "other wage types": newKey = "OTHER WAGE TYPES"; break;
                case "CHARGEABLE INCOME": newKey = "TAXABLE INCOME"; break;
                case "other deduction": newKey = "OTHER DEDUCTION"; break;
                case "Loan": // added (no dot)
                case "Loan.": newKey = "LOAN DEDUCTION"; break;
                case "Monthly Paye": newKey = "PAYE"; break;
                case "National Housing Fund": newKey = "NHF"; break;
                case "Employee Pension Contribution": newKey = "EMPLOYEE PENSION"; break;
                case "Voluntary Pension Contribution": newKey = "VOLUNTARY PENSION CONTRIBUTION"; break;
                case "Employer Pension Contribution": newKey = "EMPLOYER PENSION"; break;
                case "Net Pay": newKey = "NETPAY"; break;
                default: newKey = key; // pass-through (includes EMP ID, EMPLOYEE NAME, etc.)
            }
            renamed.put(newKey, value);
        }
        return renamed;
    }

    private static XSSFColor argb(XSSFWorkbook wb, String argbHex) {
        // argbHex like "FF0B5394" (AARRGGBB)
        String s = argbHex.startsWith("#") ? argbHex.substring(1) : argbHex;
        byte[] bytes = new byte[] {
                (byte) Integer.parseInt(s.substring(0, 2), 16),
                (byte) Integer.parseInt(s.substring(2, 4), 16),
                (byte) Integer.parseInt(s.substring(4, 6), 16),
                (byte) Integer.parseInt(s.substring(6, 8), 16)
        };
        return new XSSFColor(bytes, null);
    }

//    private Map<String, Object> swapKey(Map<String, Object> result) {
//        Map<String, Object> renamedPayroll = new HashMap<>();
//        for (Map.Entry<String, Object> entry : result.entrySet()) {
//            String key = entry.getKey();
//            Object value = entry.getValue();
//            String newKey;
//
//            switch (key) {
//                case "Gross Pay":
//                    newKey = "GROSS PAY.";
//                    break;
//                case "Basic Salary":
//                    newKey = "BASIC SALARY.";
//                    break;
//                case "Housing Allowance":
//                    newKey = "HOUSING,";
//                    break;
//                case "Transport Allowance":
//                    newKey = "TRANSPORT,";
//                    break;
//                case "Utility":
//                    newKey = "UTILITY,";
//                    break;
//                case "Entertainment":
//                    newKey = "ENTERTAINMENT,";
//                    break;
//                case "Medical":
//                    newKey = "MEDICAL,";
//                    break;
//                case "PERSONAL OUTFIT":
//                    newKey = "PERSONAL OUTFIT,";
//                    break;
//                case "Leave":
//                    newKey = "LEAVE,";
//                    break;
//                case "Training":
//                    newKey = "TRAINING";
//                    break;
//                case "Monthly Performance Bonus":
//                    newKey = "PERFORMANCE BONUS";
//                    break;
//                case "overtime":
//                    newKey = "OVERTIME";
//                    break;
//                case "other variable":
//                    newKey = "OTHER VARIABLE";
//                    break;
//                case "other allowance":
//                    newKey = "OTHER ALLOWANCE";
//                    break;
//                case "other wage types":
//                    newKey = "OTHER WAGE TYPES";
//                    break;
//                case "CHARGEABLE INCOME":
//                    newKey = "TAXABLE INCOME";
//                    break;
//                case "other deduction":
//                    newKey = "OTHER DEDUCTION";
//                    break;
//                case "Loan.":
//                    newKey = "LOAN DEDUCTION";
//                    break;
//                case "Monthly Paye":
//                    newKey = "PAYE";
//                    break;
//                case "National Housing Fund":
//                    newKey = "NHF";
//                    break;
//                case "Employee Pension Contribution":
//                    newKey = "EMPLOYEE PENSION";
//                    break;
//                case "Voluntary Pension Contribution":
//                    newKey = "VOLUNTARY PENSION CONTRIBUTION";
//                    break;
//                case "Employer Pension Contribution":
//                    newKey = "EMPLOYER PENSION";
//                    break;
//                case "Net Pay":
//                    newKey = "NETPAY";
//                    break;
//                default:
//                    newKey = key;
//            }
//            renamedPayroll.put(newKey, value);
//        }
//        return renamedPayroll;
//    }
}

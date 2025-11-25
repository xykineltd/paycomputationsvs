package com.xykine.computation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykine.computation.dto.EmployeeDetail;
import com.xykine.computation.dto.PaymentDistributionItem;
import com.xykine.computation.entity.CompanyMetadata;
import com.xykine.computation.entity.Loan;
import com.xykine.computation.repo.LoanRepo;
import com.xykine.computation.request.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;

import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.repo.PayrollReportDetailRepo;
import com.xykine.computation.repo.PayrollReportSummaryRepo;

import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.utils.ReportUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.springframework.stereotype.Service;
import org.xykine.payroll.model.MapKeys;
import org.xykine.payroll.model.PaymentInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportGeneratorServiceImpl implements ReportGeneratorService {

    private final PayrollReportDetailRepo payrollReportDetailRepo;
    private final PayrollReportSummaryRepo payrollReportSummaryRepo;
    private final CompanyMetadataService companyMetadataService;
    private final ExcelUploadService excelUploadService;
    private final AdminService adminService;
    private final LoanRepo loanRepo;

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportGeneratorServiceImpl.class);


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
//            "OTHER VARIABLE",
//            "OTHER ALLOWANCE",
//            "OTHER WAGE TYPES",
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

    // Everything up to OVERTIME (fixed order)
    private static final List<String> TEMPLATE_PREFIX = List.of(
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
            "TRAINING"
//            "PERFORMANCE BONUS",
//            "OVERTIME"
            // 🔁 dynamic otherComponents will be inserted right after this
    );

    // Everything after the dynamic components
    private static final List<String> TEMPLATE_SUFFIX = List.of(
            "TAXABLE INCOME",
//            "OTHER DEDUCTION",
//            "LOAN DEDUCTION",
            "PAYE",
            "NHF",
            "EMPLOYEE PENSION",
            "VOLUNTARY PENSION CONTRIBUTION",
            "EMPLOYER PENSION",
            "NETPAY"
    );

    private static final List<String> DEDUCTION_COMPONENTS = List.of(
            "National Housing Fund",
            "Monthly Paye",
            "Paye Tax on Monthly Performance Bonus",
            "Paye Tax on Phone Bonus ",
            "Pension Fund",
            "Total Deduction",
            "NETPAY"
    );


//    Monthly Paye
//:
//        402806.04
//    National Housing Fund
//:
//        0
//    Paye Tax on Monthly Performance Bonus
//:
//        64597.71
//    Paye Tax on Phone Bonus
//:
//        5100
//    Pension Fund
//:
//        59937.11
//    Student Loan
//:
//        75000
//    Total Deduction
//:
//        607440.86
//    Total Monthly Paye
//:
//        472503.75
//    Voluntary Pension Contribution
//:
//        0


    // ✅ New: columns that should be formatted as currency (₦#,##0.00)
//    private static final Set<String> BASE_CURRENCY_COLUMNS = Set.of(
//            "GROSS PAY",
//            "GROSS SALARY",
//            "BASIC SALARY",
//            "HOUSING",
//            "TRANSPORT",
//            "UTILITY",
//            "ENTERTAINMENT",
//            "MEDICAL",
//            "PERSONAL OUTFIT",
//            "LEAVE",
//            "TRAINING",
//            "TAXABLE INCOME",
////            "OTHER DEDUCTION",
////            "LOAN DEDUCTION",
//            "PAYE",
//            "NHF",
//            "EMPLOYEE PENSION",
//            "VOLUNTARY PENSION CONTRIBUTION",
//            "EMPLOYER PENSION",
//            "NETPAY"
//    );
//
//    // Columns that should never be formatted as currency
//    private static final Set<String> TEXT_COLUMNS = Set.of(
//            "EMP ID",
//            "EMPLOYEE NAME",
//            "HIRE DATE",
//            "EXIT DATE",
//            "ROLE"
//    );

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
        final List<String> GROSS_SALARY_COMPONENTS = getMetadata(reportRequestPayload.getCompanyID());

        LocalDate today = LocalDate.now(ZoneId.of("Africa/Lagos"));

        final List<Loan> deductions = loanRepo.findActiveApprovedNonExpiredLoans(reportRequestPayload.getCompanyID(), today);
        final List<String> deductionComponents = deductions.stream().map(Loan::getDescription).toList();
        LOGGER.info("Deductions: {}", deductionComponents);

        if (reportRequestPayload.isDefaultHeaders()) {
            List<String> defaultHeaders = new LinkedList<>();
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
//            defaultHeaders.add("Monthly Performance Bonus");
//            defaultHeaders.add("overtime");
//            defaultHeaders.add("other variable");
//            defaultHeaders.add("other allowance");
//            defaultHeaders.add("other wage types");
            defaultHeaders.add("CHARGEABLE INCOME");
//            defaultHeaders.add("other deduction");
//            defaultHeaders.add("Loan");   // **
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

                if (reportRequestPayload.isAll()) {
                    source = payrollReportDetailRepo.findPayrollReportDetailByCompanyIdAndSummaryId(
                            reportRequestPayload.getCompanyID(),
                            reportRequestPayload.getReportId());
                } else if (!reportRequestPayload.getIds().isEmpty()) {
                    source = payrollReportDetailRepo.findPayrollReportDetailByEmployeeIdInAndCompanyIdAndSummaryId(
                            reportRequestPayload.getIds(),
                            reportRequestPayload.getCompanyID(),
                            reportRequestPayload.getReportId()
                    );
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
                        detail.getReportId(),
                        GROSS_SALARY_COMPONENTS,
                        deductionComponents
                ))
                .toList();

        // After dataRows is built:
        if (dataRows.isEmpty()) {
            throw new RuntimeException("No data found for selected employees/reports");
        }


        // 🔁 Build dynamic headers from any keys not in the fixed prefix/suffix
        Set<String> dynamicHeaders = dataRows.stream()
                .flatMap(row -> row.keySet().stream())
                .filter(Objects::nonNull)
                .filter(key -> !TEMPLATE_PREFIX.contains(key))
                .filter(key -> !TEMPLATE_SUFFIX.contains(key))
                .collect(Collectors.toCollection(LinkedHashSet::new)); // preserves order of discovery

        // Final headers in correct order: prefix + dynamic + suffix
        List<String> headers = new ArrayList<>();
        headers.addAll(TEMPLATE_PREFIX);
        headers.addAll(dynamicHeaders);   // this replaces the old "OTHER ALLOWANCE" placeholder
        headers.addAll(TEMPLATE_SUFFIX);



        // Make sure all template headers exist as keys (fill blanks for missing)
        // and enforce the exact template order
//        List<String> headers = new ArrayList<>(TEMPLATE_HEADERS);




//        List<Map<String, Object>> normalizedRows = new ArrayList<>(dataRows.size());
//
//        for (Map<String, Object> row : dataRows) {
//            Map<String, Object> norm = new LinkedHashMap<>();
//            for (String h : TEMPLATE_HEADERS) {
//                norm.put(h, row.getOrDefault(h, " "));
//            }
//            normalizedRows.add(norm);
//        }

        // Make sure all headers exist as keys (fill blanks for missing)
        // and enforce the final dynamic order
        List<Map<String, Object>> normalizedRows = new ArrayList<>(dataRows.size());

        for (Map<String, Object> row : dataRows) {
            Map<String, Object> norm = new LinkedHashMap<>();
            for (String h : headers) {
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

    private Map<String, Object> extractDetail(
            PaymentInfo paymentInfo,
            List<String> selectedReports,
            boolean isDetail, Map<String, EmployeeDetail> employeeDetailMap,
            String reportDetailId,
            List<String> grossSalaryComponent,
            List<String> deductionComponent
            ) {
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
        } else if (isDetail) {
            System.out.println("Employee detail is null for employeeId: " + employeeId);
        }

        Map<String, Object> finalResult = new LinkedHashMap<>(result);
        selectedReports.forEach(key -> {
            Object value = raw.getOrDefault(key, " ");
            finalResult.put(key, value);
        });

        finalResult.put("GROSS SALARY", deriveGrossSalary(paymentInfo.getGrossPay(), grossSalaryComponent));
        final Map<String, BigDecimal> otherComponents = getOtherComponents(paymentInfo.getGrossPay(), grossSalaryComponent);
        final Map<String, BigDecimal> otherDeductions = getRemainingDeductions(paymentInfo.getDeduction(), deductionComponent);

        //  Add each otherComponent into the row so they can become columns
        finalResult.putAll(otherComponents);
        finalResult.putAll(otherDeductions);

        return swapKey(finalResult);
    }

    private List<String> getMetadata (String companyId) {
        CompanyMetadata data = companyMetadataService.geCompanyMetadataById(companyId);
        return getDistributionNames(data);
    }

    public List<PaymentDistributionItem> toDistributionList(CompanyMetadata meta) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(
                    meta.getPaymentDistribution(),
                    new TypeReference<>() {
                    }
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse paymentDistribution", e);
        }
    }

    public List<String> getDistributionNames(CompanyMetadata meta) {
        return toDistributionList(meta).stream()
                .map(PaymentDistributionItem::getName)
                .toList();
    }


//    //Gross Salary is the summation of gross pay
//    private BigDecimal deriveGrossSalary(Map<String, BigDecimal> grossPay) {
//        if (grossPay == null || grossPay.isEmpty()) {
//            return BigDecimal.ZERO;
//        }
//
//        return grossPay.entrySet().stream()
//                .filter(e ->
//                        !"Gross Pay".equalsIgnoreCase(e.getKey()) &&
//                                !"Monthly Performance Bonus".equalsIgnoreCase(e.getKey()))
//                .map(Map.Entry::getValue)
//                .filter(Objects::nonNull)
//                .reduce(BigDecimal.ZERO, BigDecimal::add);
//    }

    private BigDecimal deriveGrossSalary(Map<String, BigDecimal> grossPay, List<String> grossSalaryComponent) {
        if (grossPay == null || grossPay.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return grossPay.entrySet().stream()
                .filter(e ->
                        e.getKey() != null &&
                                grossSalaryComponent.contains(e.getKey().trim()))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, BigDecimal> getOtherComponents(Map<String, BigDecimal> grossPay, List<String> grossSalaryComponent) {
        if (grossPay == null || grossPay.isEmpty()) {
            return Map.of(); // empty immutable map
        }

        return grossPay.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .filter(e -> !"Gross Pay".equalsIgnoreCase(e.getKey()))
                .filter(e -> !grossSalaryComponent.contains(e.getKey().trim()))
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    private Map<String, BigDecimal> getRemainingDeductions(
            Map<String, BigDecimal> deductionMap,
            List<String> deductionComponents) {

        if (deductionMap == null || deductionMap.isEmpty()) {
            return Collections.emptyMap();
        }

        return deductionMap.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .filter(e -> deductionComponents.contains(e.getKey().trim()))  // ❌ not in base list
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
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
                .filter(Objects::nonNull)
                .forEach(raw::putAll);

        return raw;
    }

    private byte[] generateExcel(List<String> headers, List<Map<String, Object>> dataRows, String fileName) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet("Report");

            // === Colors (ARGB). ===
            final String HEADER_BLUE  = "FF1F4E79";  // dark blue; white text recommended

            // --- Header style ---
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

            // --- Row 0 left empty ---
            sheet.createRow(0);

            // --- Row 1: Headers (all blue) ---
            Row headerRow = sheet.createRow(1);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(headerStyle);
            }

            // 🔢 Number style (no currency symbol)
            XSSFCellStyle numberStyle = workbook.createCellStyle();
            XSSFDataFormat format = workbook.createDataFormat();
            numberStyle.setDataFormat(format.getFormat("#,##0.00"));

            // --- Rows 2+: Data ---
            for (int i = 0; i < dataRows.size(); i++) {
                Row row = sheet.createRow(i + 2); // shifted by 2 (empty + header)
                Map<String, Object> rowData = dataRows.get(i);

                for (int j = 0; j < headers.size(); j++) {
                    String headerName = headers.get(j);
                    Object value = rowData.get(headerName);
                    Cell cell = row.createCell(j);

                    if (value instanceof Number number) {
                        cell.setCellValue(number.doubleValue());
                        // always use plain number style (no ₦)
                        cell.setCellStyle(numberStyle);
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

            // Freeze top 2 rows (empty row 0 + header row 1)
            sheet.createFreezePane(0, 2);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

//    private byte[] generateExcel(List<String> headers, List<Map<String, Object>> dataRows, String fileName) throws IOException {
//        try (XSSFWorkbook workbook = new XSSFWorkbook();
//             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
//
//            XSSFSheet sheet = workbook.createSheet("Report");
//
//            // === Colors (ARGB). ===
//            final String HEADER_BLUE  = "FF1F4E79";  // dark blue; white text recommended
//
//            // --- Styles ---
//            // Header (blue) style
//            XSSFCellStyle headerStyle = workbook.createCellStyle();
//            XSSFFont headerFont = workbook.createFont();
//            headerFont.setBold(true);
//            headerFont.setColor(IndexedColors.WHITE.getIndex());
//            headerStyle.setFont(headerFont);
//            headerStyle.setAlignment(HorizontalAlignment.CENTER);
//            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
//            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
//            headerStyle.setFillForegroundColor(argb(workbook, HEADER_BLUE));
//            headerStyle.setBorderBottom(BorderStyle.THIN);
//            headerStyle.setBorderTop(BorderStyle.THIN);
//            headerStyle.setBorderLeft(BorderStyle.THIN);
//            headerStyle.setBorderRight(BorderStyle.THIN);
//
//            // --- Row 0 left empty ---
//            sheet.createRow(0);
//
//            // --- Row 1: Headers (all blue) ---
//            Row headerRow = sheet.createRow(1);
//            for (int i = 0; i < headers.size(); i++) {
//                Cell c = headerRow.createCell(i);
//                c.setCellValue(headers.get(i));
//                c.setCellStyle(headerStyle);
//            }
//
//            // Number style
//            XSSFCellStyle numberStyle = workbook.createCellStyle();
//            XSSFDataFormat format = workbook.createDataFormat();
//            numberStyle.setDataFormat(format.getFormat("#,##0.00"));
//
//            // Currency style ₦
//            XSSFCellStyle currencyStyle = workbook.createCellStyle();
//            currencyStyle.cloneStyleFrom(numberStyle);
////            currencyStyle.setDataFormat(format.getFormat("₦#,##0.00"));
//
//            // 🔹 Build runtime currency set = base + dynamic headers
//            Set<String> currencyColumns = new HashSet<>(BASE_CURRENCY_COLUMNS);
//
//            // Any header that is not a pure text column and is not already in base,
//            // we treat as currency (this will include all dynamic components like PHONE ALLOWANCE)
//            for (String h : headers) {
//                if (!TEXT_COLUMNS.contains(h)) {
//                    currencyColumns.add(h);
//                }
//            }
//
//            // --- Rows 2+: Data ---
//            for (int i = 0; i < dataRows.size(); i++) {
//                Row row = sheet.createRow(i + 2); // shifted up by 1
//                Map<String, Object> rowData = dataRows.get(i);
//
//                for (int j = 0; j < headers.size(); j++) {
//                    String headerName = headers.get(j);
//                    Object value = rowData.get(headerName);
//                    Cell cell = row.createCell(j);
//
//                    if (value instanceof Number number) {
//                        cell.setCellValue(number.doubleValue());
//
//                        // Use dynamic currencyColumns instead of static set
//                        if (currencyColumns.contains(headerName)) {
//                            cell.setCellStyle(currencyStyle);
//                        } else {
//                            cell.setCellStyle(numberStyle);
//                        }
//                    } else if (value != null) {
//                        cell.setCellValue(value.toString());
//                    } else {
//                        cell.setBlank();
//                    }
//                }
//            }
//
//            // Autosize + minimum width
//            for (int i = 0; i < headers.size(); i++) {
//                sheet.autoSizeColumn(i);
//                int currentWidth = sheet.getColumnWidth(i);
//                int minWidth = 22 * 256; // ~22 chars
//                if (currentWidth < minWidth) sheet.setColumnWidth(i, minWidth);
//            }
//
//            // Freeze top 2 rows (empty row 0 + header row 1)
//            sheet.createFreezePane(0, 2);
//
//            workbook.write(outputStream);
//            return outputStream.toByteArray();
//        }
//    }

    private Map<String, Object> swapKey(Map<String, Object> result) {
        // Preserve insertion order so header building sees a stable order
        Map<String, Object> renamed = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : result.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key == null) {
                continue;
            }

            String normalized = key.trim();
            String newKey;

            switch (normalized) {
                case "Gross Pay" -> newKey = "GROSS PAY";
                case "Basic Salary" -> newKey = "BASIC SALARY";
                case "GROSS SALARY" -> newKey = "GROSS SALARY";
                case "Housing" -> newKey = "HOUSING";
                case "Transport" -> newKey = "TRANSPORT";
                case "Utility" -> newKey = "UTILITY";
                case "Entertainment" -> newKey = "ENTERTAINMENT";
                case "Medical" -> newKey = "MEDICAL";
                case "Personal Outfit" -> newKey = "PERSONAL OUTFIT";
                case "Leave" -> newKey = "LEAVE";
                case "Training" -> newKey = "TRAINING";
//                case "Monthly Performance Bonus" -> newKey = "PERFORMANCE BONUS";
//                case "overtime" -> newKey = "OVERTIME";
                case "CHARGEABLE INCOME" -> newKey = "TAXABLE INCOME";
                case "Loan", "Loan." -> newKey = "LOAN DEDUCTION";
                case "other deduction" -> newKey = "OTHER DEDUCTION";
                case "Monthly Paye" -> newKey = "PAYE";
                case "National Housing Fund" -> newKey = "NHF";
                case "Employee Pension Contribution" -> newKey = "EMPLOYEE PENSION";
                case "Voluntary Pension Contribution" -> newKey = "VOLUNTARY PENSION CONTRIBUTION";
                case "Employer Pension Contribution" -> newKey = "EMPLOYER PENSION";
                case "Net Pay" -> newKey = "NETPAY";

                default -> {
                    // 🔁 For ANY dynamic / unknown component, normalize to UPPER CASE
                    // e.g. "Phone Allowance" -> "PHONE ALLOWANCE"
                    newKey = normalized.toUpperCase(Locale.ROOT);
                }
            }

            renamed.put(newKey, value);
        }

        return renamed;
    }


//    private Map<String, Object> swapKey(Map<String, Object> result) {
//        Map<String, Object> renamed = new HashMap<>();
//        for (Map.Entry<String, Object> entry : result.entrySet()) {
//            String key = entry.getKey();
//            Object value = entry.getValue();
//            String newKey;
//
//            switch (key) {
//                case "Gross Pay": newKey = "GROSS PAY"; break;
//                case "Basic Salary": newKey = "BASIC SALARY"; break;
//                case "GROSS SALARY": newKey = "GROSS SALARY"; break;
//                case "Housing": newKey = "HOUSING"; break;
//                case "Transport": newKey = "TRANSPORT"; break;
//                case "Utility": newKey = "UTILITY"; break;
//                case "Entertainment": newKey = "ENTERTAINMENT"; break;
//                case "Medical": newKey = "MEDICAL"; break;
//                case "Personal Outfit": newKey = "PERSONAL OUTFIT"; break;
//                case "Leave": newKey = "LEAVE"; break;
//                case "Training": newKey = "TRAINING"; break;
//                case "Monthly Performance Bonus": newKey = "PERFORMANCE BONUS"; break;
//                case "overtime": newKey = "OVERTIME"; break;
//                case "other variable": newKey = "OTHER VARIABLE"; break;
//                case "other allowance": newKey = "OTHER ALLOWANCE"; break;
//                case "other wage types": newKey = "OTHER WAGE TYPES"; break;
//                case "CHARGEABLE INCOME": newKey = "TAXABLE INCOME"; break;
//                case "Loan":
//                case "Loan.": newKey = "LOAN DEDUCTION"; break;
//                case "other deduction": newKey = "OTHER DEDUCTION"; break;
//                case "Monthly Paye": newKey = "PAYE"; break;
//                case "National Housing Fund": newKey = "NHF"; break;
//                case "Employee Pension Contribution": newKey = "EMPLOYEE PENSION"; break;
//                case "Voluntary Pension Contribution": newKey = "VOLUNTARY PENSION CONTRIBUTION"; break;
//                case "Employer Pension Contribution": newKey = "EMPLOYER PENSION"; break;
//                case "Net Pay": newKey = "NETPAY"; break;
//                default: newKey = key;
//            }
//            renamed.put(newKey, value);
//        }
//        return renamed;
//    }

    private static XSSFColor argb(XSSFWorkbook wb, String argbHex) {
        String s = argbHex.startsWith("#") ? argbHex.substring(1) : argbHex;
        byte[] bytes = new byte[] {
                (byte) Integer.parseInt(s.substring(0, 2), 16),
                (byte) Integer.parseInt(s.substring(2, 4), 16),
                (byte) Integer.parseInt(s.substring(4, 6), 16),
                (byte) Integer.parseInt(s.substring(6, 8), 16)
        };
        return new XSSFColor(bytes, null);
    }
}

package com.xykine.computation.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.repo.PayrollReportDetailRepo;
import com.xykine.computation.repo.PayrollReportSummaryRepo;
import com.xykine.computation.request.DateRange;
import com.xykine.computation.request.ReportRequestPayload;
import com.xykine.computation.request.RetrievePaymentElementPayload;
import com.xykine.computation.request.RetrieveSummaryElementRequest;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportGeneratorServiceImpl.class);


    @Override
    public byte[] generateReport(ReportRequestPayload reportRequestPayload) throws IOException {
        if (reportRequestPayload.getEntityType() == null) {
            throw new RuntimeException("Report type is mandatory");
        }

        if (reportRequestPayload.getCompanyID() == null) {
            throw new RuntimeException("CompanyId is mandatory");
        }

        List<?> source; // raw entities before transform

        LOGGER.info("source ---> reportRequestPayload.isAll(): {}", reportRequestPayload.isAll());

        // Decide source based on type + flags
        switch (reportRequestPayload.getEntityType()) {
            case "details" -> {

                List<String> headers = reportRequestPayload.getHeaders();
                if (reportRequestPayload.isAll()) {
                    source = payrollReportDetailRepo.findByCompanyId(reportRequestPayload.getCompanyID());
                    LOGGER.info("source --->: {}", source);
                } else if (!reportRequestPayload.getIds().isEmpty()) {
                    source = payrollReportDetailRepo.findPayrollReportDetailByEmployeeIdInAndCompanyId(reportRequestPayload.getIds(), reportRequestPayload.getCompanyID());
                } else {
                    source = List.of();
                    LOGGER.info("source ---> empty: {}", source);
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
                        isDetail.get()
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
                .map(detail -> extractDetail(detail.getDetail().getReport(), retrievePaymentElementPayload.getSelectedHeader(), true))
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
        LOGGER.info("Date range--->: {}", dateRange);
        LOGGER.info("Detail--->: {}", detail);

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

    private Map<String, Object> extractDetail(PaymentInfo paymentInfo, List<String> selectedReports, boolean isDetail) {
        Map<String, Object> raw = extractRawDetail(paymentInfo);
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

            // 🔹 Styles
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setAlignment(HorizontalAlignment.LEFT);

            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.setAlignment(HorizontalAlignment.RIGHT);
            DataFormat df = workbook.createDataFormat();
            numberStyle.setDataFormat(df.getFormat("#,##0.00")); // two decimal places

            // 🔹 Header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // 🔹 Data rows
            for (int i = 0; i < dataRows.size(); i++) {
                Row row = sheet.createRow(i + 1);
                Map<String, Object> rowData = dataRows.get(i);

                for (int j = 0; j < headers.size(); j++) {
                    Object value = rowData.get(headers.get(j));
                    Cell cell = row.createCell(j);

                    if (value instanceof Number) {
                        cell.setCellValue(((Number) value).doubleValue());
                        cell.setCellStyle(numberStyle);
                    } else if (value != null) {
                        cell.setCellValue(value.toString());
                        cell.setCellStyle(textStyle);
                    } else {
                        cell.setBlank();
                        cell.setCellStyle(textStyle);
                    }
                }
            }

            // 🔹 Auto-size all columns, but enforce a minimum width (15 chars)
            int totalCols = headers.size();
            for (int i = 0; i < totalCols; i++) {
                sheet.autoSizeColumn(i);
                int currentWidth = sheet.getColumnWidth(i);
                int minWidth = 15 * 256; // 15 characters
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

}
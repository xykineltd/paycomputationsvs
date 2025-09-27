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

import com.xykine.computation.response.GeneratedReportResponse;
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

@Service
@RequiredArgsConstructor
public class ReportGeneratorServiceImpl implements ReportGeneratorService {

    private final PayrollReportDetailRepo payrollReportDetailRepo;
    private final PayrollReportSummaryRepo payrollReportSummaryRepo;
    private final ExcelUploadService excelUploadService;

    @Override
    public byte[] generateReport(ReportRequestPayload reportRequestPayload) throws IOException {
        if (reportRequestPayload.getEntityType() == null) {
            throw new RuntimeException("Report type is mandatory");
        }

        if (reportRequestPayload.getCompanyID() == null) {
            throw new RuntimeException("CompanyId is mandatory");
        }

        List<?> source; // raw entities before transform

        // Decide source based on type + flags
        switch (reportRequestPayload.getEntityType()) {
            case "details" -> {
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

        // Transform, filter, and map into data rows
        List<Map<String, Object>> dataRows = source.stream()
                .filter(Objects::nonNull)
                .map(obj -> {
                    if (obj instanceof PayrollReportDetail detail) {
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
                        reportRequestPayload.getHeaders()
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
                .map(detail -> extractDetail(detail.getDetail().getReport(), retrievePaymentElementPayload.getSelectedHeader()))
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

    private Map<String, Object> extractDetail(PaymentInfo paymentInfo, List<String> selectedReports) {
        Map<String, Object> raw = extractRawDetail(paymentInfo);
        Map<String, Object> result = new LinkedHashMap<>();

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

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}

package com.xykine.computation.service;

import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.repo.PayrollReportDetailRepo;
import com.xykine.computation.request.ReportRequestPayload;
import com.xykine.computation.response.GeneratedReportResponse;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.utils.ReportUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.xykine.payroll.model.MapKeys;
import org.xykine.payroll.model.PaymentInfo;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportGeneratorServiceImpl implements ReportGeneratorService {

    private final PayrollReportDetailRepo payrollReportDetailRepo;
    private final ExcelUploadService excelUploadService;

    @Override
    public void generateReport(ReportRequestPayload reportRequestPayload) {
        List<Map<String, Object>> dataRows = payrollReportDetailRepo
                .findPayrollReportDetailBySummaryId(reportRequestPayload.getReportId()).stream()
                .filter(Objects::nonNull)
                .map(ReportUtils::transform)
                .filter(detail -> shouldIncludeEmployee(detail, reportRequestPayload))
                .map(detail -> extractDetail(detail.getDetail().getReport(), reportRequestPayload.getSelectedReports()))
                .toList();

        if (dataRows.isEmpty()) {
            throw new RuntimeException("No data found for selected employees/reports");
        }

        List<String> headers = new ArrayList<>(dataRows.get(0).keySet());

        try {
            String fileName = reportRequestPayload.getReportId() + ".xlsx";
            excelUploadService.generateAndUploadExcel(headers, dataRows, fileName);
        } catch (IOException e) {
            throw new RuntimeException("Error generating or uploading report: " + e.getMessage(), e);
        }
    }

    private boolean shouldIncludeEmployee(ReportResponse detail, ReportRequestPayload payload) {
        return payload.isAllEmployee() || payload.getEmployeeIds().contains(detail.getEmployeeId());
    }

    private Map<String, Object> extractDetail(PaymentInfo paymentInfo, List<String> selectedReports) {
        Map<String, BigDecimal> raw = new HashMap<>();
        raw.put(MapKeys.NET_PAY, paymentInfo.getNetPay());

        List<Map<String, BigDecimal>> components = List.of(
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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("EmployeeId", paymentInfo.getEmployeeID());
        result.put("FullName", paymentInfo.getFullName());
        result.put("StartDate", paymentInfo.getStartDate());
        result.put("EndDate", paymentInfo.getEndDate());

        selectedReports.forEach(key -> {
            if (raw.containsKey(key)) {
                result.put(key, raw.get(key));
            }
        });

        return result;
    }
}

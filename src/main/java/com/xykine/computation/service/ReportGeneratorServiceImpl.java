package com.xykine.computation.service;

import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.repo.PayrollReportDetailRepo;
import com.xykine.computation.request.ReportRequestPayload;
import com.xykine.computation.request.RetrievePaymentElementPayload;
import com.xykine.computation.response.GeneratedReportResponse;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.utils.ReportUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
                .map(detail -> extractDetail(detail.getDetail().getReport(), reportRequestPayload.getSelectedHeader()))
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

    private boolean shouldIncludeEmployee(ReportResponse detail, ReportRequestPayload payload) {
        return payload.isAllEmployee() || payload.getEmployeeIds().contains(detail.getEmployeeId());
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
}

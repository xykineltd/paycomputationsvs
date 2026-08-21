package com.xykine.computation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykine.computation.dto.EmployeeDetail;
import com.xykine.computation.dto.PaymentDistributionItem;
import com.xykine.computation.entity.CompanyMetadata;
import com.xykine.computation.entity.Loan;
import com.xykine.computation.entity.YTDReport;
import com.xykine.computation.repo.LoanRepo;
import com.xykine.computation.repo.YTDReportRepo;
import com.xykine.computation.request.*;
import com.xykine.computation.utils.AppUtil;
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
    private final YTDReportRepo ytdReportRepo;

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportGeneratorServiceImpl.class);

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
            "Total PAYE",
            "Paye Tax on Monthly Performance Bonus",
            "Paye Tax on Phone Bonus ",
            "Pension Fund",
            "Total Deduction",
            "NETPAY"
    );

    private static final List<String> DEFAULT_PAYMENT_HEADERS = List.of(
            "Gross Pay",
            "Basic Salary",
            "Housing",
            "Transport",
            "Utility",
            "Entertainment",
            "Medical",
            "Personal Outfit",
            "Leave",
            "Training",
            "MONTHLY CHARGEABLE INCOME",
            "Total PAYE",
            "National Housing Fund",
            "Employee Pension Contribution",
            "Voluntary Pension Contribution",
            "Employer Pension Contribution",
            "Net Pay"
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

        Map<String, EmployeeDetail> loadedEmployeeDetails = adminService.getEmployeesDetail(employeeFilterRequest, token);
        final Map<String, EmployeeDetail> employeeDetailMap =
                loadedEmployeeDetails != null ? loadedEmployeeDetails : Map.of();
        LOGGER.info("Loaded {} employee records from admin svc for report type {}",
                employeeDetailMap.size(), reportRequestPayload.getEntityType());
        final List<String> GROSS_SALARY_COMPONENTS = getMetadata(reportRequestPayload.getCompanyID());

        LocalDate today = LocalDate.now(ZoneId.of("Africa/Lagos"));

        final List<Loan> deductions = loanRepo.findActiveApprovedNonExpiredLoans(reportRequestPayload.getCompanyID(), today);
        final List<String> deductionComponents = deductions.stream().map(Loan::getDescription).toList();
        LOGGER.info("Deductions: {}", deductionComponents);

        if (reportRequestPayload.isDefaultHeaders()) {
            reportRequestPayload.setHeaders(new LinkedList<>(DEFAULT_PAYMENT_HEADERS));
        }

        List<?> source; // raw entities before transform

        String entityType = reportRequestPayload.getEntityType().trim().toLowerCase(Locale.ROOT);

        // Decide source based on type + flags
        switch (entityType) {
            case "details", "itf", "nsitf", "nhf", "pension", "monthly", "ytd", "paye", "bank-file", "bank_file" -> {

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

        final Map<String, YTDReport> ytdByEmployeeId = loadYtdReports(
                entityType,
                source,
                reportRequestPayload.getCompanyID()
        );

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
                // Allow all details entry in the downloaded reports
//                .filter(detail -> filterByDates(detail, reportRequestPayload))
                .map(detail -> {
                    if ("nsitf".equals(entityType)) {
                        return extractNsitfDetail(detail.getDetail().getReport(), employeeDetailMap);
                    }
                    if ("itf".equals(entityType)) {
                        return extractItfDetail(detail.getDetail().getReport(), employeeDetailMap, ytdByEmployeeId);
                    }
                    if ("paye".equals(entityType)) {
                        return extractPayeDetail(
                                detail.getDetail().getReport(),
                                employeeDetailMap,
                                GROSS_SALARY_COMPONENTS
                        );
                    }
                    if ("ytd".equals(entityType)) {
                        return extractYtdDetail(
                                detail.getDetail().getReport(),
                                employeeDetailMap,
                                ytdByEmployeeId
                        );
                    }
                    return extractDetail(
                            detail.getDetail().getReport(),
                            reportRequestPayload.getHeaders(),
                            isDetail.get(),
                            employeeDetailMap,
                            detail.getReportId(),
                            GROSS_SALARY_COMPONENTS,
                            deductionComponents
                    );
                })
                .toList();

        // After dataRows is built:
        if (dataRows.isEmpty()) {
            throw new RuntimeException("No data found for selected employees/reports");
        }


        boolean useRequestedHeaders = !reportRequestPayload.isDefaultHeaders()
                && reportRequestPayload.getHeaders() != null
                && !reportRequestPayload.getHeaders().isEmpty();

        List<String> headers;
        if (useRequestedHeaders) {
            headers = new ArrayList<>(reportRequestPayload.getHeaders());
        } else {
            // 🔁 Build dynamic headers from any keys not in the fixed prefix/suffix
            Set<String> dynamicHeaders = dataRows.stream()
                    .flatMap(row -> row.keySet().stream())
                    .filter(Objects::nonNull)
                    .filter(key -> !TEMPLATE_PREFIX.contains(key))
                    .filter(key -> !TEMPLATE_SUFFIX.contains(key))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            headers = new ArrayList<>();
            headers.addAll(TEMPLATE_PREFIX);
            headers.addAll(dynamicHeaders);
            headers.addAll(TEMPLATE_SUFFIX);
        }



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
        int serialNumber = 1;

        for (Map<String, Object> row : dataRows) {
            Map<String, Object> norm = new LinkedHashMap<>();
            for (String h : headers) {
                if (isSerialNumberHeader(h)) {
                    norm.put(h, serialNumber);
                } else if (useRequestedHeaders) {
                    norm.put(h, resolveColumnValue(row, h));
                } else {
                    norm.put(h, row.getOrDefault(h, " "));
                }
            }
            normalizedRows.add(norm);
            serialNumber++;
        }


        // file name stays same
        String fileName = reportRequestPayload.getCompanyID() + "_" +
                reportRequestPayload.getEntityType() + "_" +
                reportRequestPayload.getDateRange().getStart() + "_" +
                reportRequestPayload.getDateRange().getEnd() + ".xlsx";

        return generateExcel(headers, normalizedRows, fileName);

    }

    @Override
    public List<Map<String, Object>> loadPaymentInfoRowsForReport(String companyId, String reportId, String token) {
        if (companyId == null || companyId.isBlank() || reportId == null || reportId.isBlank()) {
            return List.of();
        }

        EmployeeFilterRequest employeeFilterRequest = new EmployeeFilterRequest();
        employeeFilterRequest.setCompanyID(companyId);
        Map<String, EmployeeDetail> employeeDetailMap = Map.of();
        try {
            if (token != null && !token.isBlank()) {
                employeeDetailMap = Optional.ofNullable(
                        adminService.getEmployeesDetail(employeeFilterRequest, token)
                ).orElse(Map.of());
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to load employee details for report {}: {}", reportId, e.getMessage());
        }

        final List<String> grossSalaryComponents = getMetadata(companyId);
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Lagos"));
        final List<String> deductionComponents = loanRepo
                .findActiveApprovedNonExpiredLoans(companyId, today)
                .stream()
                .map(Loan::getDescription)
                .toList();

        List<PayrollReportDetail> source = payrollReportDetailRepo
                .findPayrollReportDetailByCompanyIdAndSummaryId(companyId, reportId);
        if (source == null || source.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (PayrollReportDetail detail : source) {
            if (detail == null) continue;
            try {
                ReportResponse transformed = ReportUtils.transform(detail);
                if (transformed == null || transformed.getDetail() == null || transformed.getDetail().getReport() == null) {
                    continue;
                }
                PaymentInfo paymentInfo = transformed.getDetail().getReport();
                Map<String, Object> raw = extractRawDetail(paymentInfo, transformed.getReportId());
                List<String> selected = new LinkedList<>(DEFAULT_PAYMENT_HEADERS);
                raw.keySet().stream()
                        .filter(Objects::nonNull)
                        .filter(k -> !selected.contains(k))
                        .forEach(selected::add);

                Map<String, Object> row = extractDetail(
                        paymentInfo,
                        selected,
                        true,
                        employeeDetailMap,
                        transformed.getReportId(),
                        grossSalaryComponents,
                        deductionComponents
                );
                if (row.get("EMP ID") == null || String.valueOf(row.get("EMP ID")).isBlank()) {
                    row.put("EMP ID", paymentInfo.getEmployeeID());
                }
                if (row.get("EMPLOYEE NAME") == null) {
                    row.put("EMPLOYEE NAME", paymentInfo.getFullName());
                }
                raw.forEach((k, v) -> {
                    if (k != null) row.putIfAbsent(k, v);
                });
                rows.add(row);
            } catch (Exception e) {
                LOGGER.warn("Skipping payroll detail {} while building PaymentInfo rows: {}",
                        detail.getId(), e.getMessage());
            }
        }
        return rows;
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
        return !startDateInstance.isBefore(dateRange.getStart())
                && !startDateInstance.isAfter(dateRange.getEnd());
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

    private Map<String, Object> extractNsitfDetail(
            PaymentInfo paymentInfo,
            Map<String, EmployeeDetail> employeeDetailMap
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        EmployeeDetail employee = employeeDetailMap != null
                ? employeeDetailMap.get(paymentInfo.getEmployeeID())
                : null;

        row.put("EMP ID", employee != null && hasValue(employee.getMappedId())
                ? employee.getMappedId()
                : paymentInfo.getEmployeeID());
        row.put("EMPLOYEE NAME", firstNonBlank(
                employee != null ? employee.getName() : null,
                paymentInfo.getFullName()
        ));
        row.put("DATE OF BIRTH", employee != null ? formatDateOfBirth(employee.getDateOfBirth()) : "");
        row.put("SEX", employee != null && employee.getSex() != null ? employee.getSex() : "");

        BigDecimal grossPay = mapDecimal(paymentInfo.getGrossPay(), "Gross Pay", MapKeys.GROSS_PAY, "GROSS PAY");
        row.put("GROSS PAY", grossPay != null ? grossPay : " ");
        row.put("GROSS INCOME", grossPay != null ? grossPay : " ");
        row.put("NSITF", grossPay != null ? grossPay.multiply(new BigDecimal("0.01")) : " ");

        return row;
    }

    private Map<String, Object> extractItfDetail(
            PaymentInfo paymentInfo,
            Map<String, EmployeeDetail> employeeDetailMap,
            Map<String, YTDReport> ytdByEmployeeId
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        EmployeeDetail employee = employeeDetailMap != null
                ? employeeDetailMap.get(paymentInfo.getEmployeeID())
                : null;
        YTDReport ytd = ytdByEmployeeId != null
                ? ytdByEmployeeId.get(paymentInfo.getEmployeeID())
                : null;

        row.put("EMP ID", employee != null && hasValue(employee.getMappedId())
                ? employee.getMappedId()
                : paymentInfo.getEmployeeID());
        row.put("EMPLOYEE NAME", firstNonBlank(
                employee != null ? employee.getName() : null,
                paymentInfo.getFullName()
        ));
        row.put("ROLE", employee != null && employee.getRole() != null ? employee.getRole() : "");

        BigDecimal ytdGrossPay = ytd != null ? ytd.getGrossPay() : null;
        BigDecimal ytdEmployeePension = ytd != null ? ytd.getEmployeePension() : null;
        BigDecimal ytdEmployerPension = ytd != null ? ytd.getEmployerPension() : null;

        row.put("GROSS SALARY (YTD)", ytdGrossPay != null ? ytdGrossPay : " ");
        row.put("YTD EMPLOYEE PENSION @ 8%", ytdEmployeePension != null ? ytdEmployeePension : " ");

        if (ytdGrossPay != null) {
            BigDecimal ytdNsitf = ytdGrossPay.multiply(new BigDecimal("0.01"));
            BigDecimal employerPension = ytdEmployerPension != null ? ytdEmployerPension : BigDecimal.ZERO;
            row.put("ITF", employerPension.add(ytdNsitf).multiply(new BigDecimal("0.01")));
        } else {
            row.put("ITF", " ");
        }

        return row;
    }

    private Map<String, Object> extractYtdDetail(
            PaymentInfo paymentInfo,
            Map<String, EmployeeDetail> employeeDetailMap,
            Map<String, YTDReport> ytdByEmployeeId
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        EmployeeDetail employee = employeeDetailMap != null
                ? employeeDetailMap.get(paymentInfo.getEmployeeID())
                : null;
        YTDReport ytd = ytdByEmployeeId != null
                ? ytdByEmployeeId.get(paymentInfo.getEmployeeID())
                : null;

        row.put("EMP ID", employee != null && hasValue(employee.getMappedId())
                ? employee.getMappedId()
                : paymentInfo.getEmployeeID());
        row.put("EMPLOYEE NAME", firstNonBlank(
                employee != null ? employee.getName() : null,
                paymentInfo.getFullName()
        ));
        row.put("ROLE", employee != null && employee.getRole() != null ? employee.getRole() : "");

        row.put("GROSS SALARY", moneyOrBlank(ytd != null ? ytd.getGrossPay() : null));
        row.put("GROSS SALARY (MONTHLY)", moneyOrBlank(ytd != null ? ytd.getGrossPay() : null));
        row.put("NHF", moneyOrBlank(ytd != null ? ytd.getNhf() : null));
        row.put("EMPLOYEE PENSION", moneyOrBlank(ytd != null ? ytd.getEmployeePension() : null));
        row.put("VOLUNTARY PENSION CONTRIBUTION", moneyOrBlank(ytd != null ? ytd.getVoluntarPensionContribution() : null));
        row.put("TAXABLE INCOME", moneyOrBlank(ytd != null ? ytd.getTaxableIncome() : null));
        row.put("PAYE TAX", moneyOrBlank(ytd != null ? ytd.getPayeeTax() : null));
        row.put("PAYE", moneyOrBlank(ytd != null ? ytd.getPayeeTax() : null));
        row.put("NETPAY", moneyOrBlank(ytd != null ? ytd.getNetPay() : null));
        row.put("EMPLOYER PENSION", moneyOrBlank(ytd != null ? ytd.getEmployerPension() : null));

        BigDecimal deductionsTotal = ytdDeductionsTotal(ytd != null ? ytd.getDeductions() : null);
        row.put("DEDUCTIONS", moneyOrBlank(deductionsTotal));
        row.put("TOTAL DEDUCTION", moneyOrBlank(deductionsTotal));

        return row;
    }

    private static BigDecimal ytdDeductionsTotal(Map<String, BigDecimal> deductions) {
        if (deductions == null || deductions.isEmpty()) {
            return null;
        }
        BigDecimal total = mapDecimalIgnoreCase(deductions, "Total Deduction", "Total Deductions", "TOTAL DEDUCTION");
        if (total != null) {
            return total;
        }
        return deductions.values().stream()
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, YTDReport> loadYtdReports(String entityType, List<?> source, String companyId) {
        if ((!"itf".equals(entityType) && !"ytd".equals(entityType))
                || source == null
                || source.isEmpty()
                || companyId == null) {
            return Map.of();
        }

        List<String> employeeIds = source.stream()
                .filter(PayrollReportDetail.class::isInstance)
                .map(obj -> ((PayrollReportDetail) obj).getEmployeeId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (employeeIds.isEmpty()) {
            return Map.of();
        }

        return ytdReportRepo.findYTDReportByEmployeeIdInAndCompanyId(employeeIds, companyId).stream()
                .filter(Objects::nonNull)
                .filter(ytd -> ytd.getEmployeeId() != null)
                .collect(Collectors.toMap(YTDReport::getEmployeeId, ytd -> ytd, (a, b) -> a));
    }

    private Map<String, Object> extractPayeDetail(
            PaymentInfo paymentInfo,
            Map<String, EmployeeDetail> employeeDetailMap,
            List<String> grossSalaryComponent
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        EmployeeDetail employee = employeeDetailMap != null
                ? employeeDetailMap.get(paymentInfo.getEmployeeID())
                : null;

        row.put("EMP ID", employee != null && hasValue(employee.getMappedId())
                ? employee.getMappedId()
                : paymentInfo.getEmployeeID());
        row.put("EMPLOYEE NAME", firstNonBlank(
                employee != null ? employee.getName() : null,
                paymentInfo.getFullName()
        ));
        row.put("ROLE", employee != null && employee.getRole() != null ? employee.getRole() : "");
        row.put("STATE OF RESIDENCE", employee != null ? firstNonBlank(employee.getStateOfResidence()) : "");
        row.put("TAX ID", employee != null ? firstNonBlank(employee.getTaxId()) : "");

        BigDecimal derivedGrossSalary = deriveGrossSalary(paymentInfo.getGrossPay(), grossSalaryComponent);
        BigDecimal grossPay = amountFrom(paymentInfo, "Gross Pay", MapKeys.GROSS_PAY, "GROSS PAY");
        BigDecimal monthlyGrossSalary = derivedGrossSalary != null && derivedGrossSalary.compareTo(BigDecimal.ZERO) != 0
                ? derivedGrossSalary
                : grossPay;

        row.put("GROSS SALARY", moneyOrBlank(monthlyGrossSalary));
        row.put("GROSS SALARY (MONTHLY)", moneyOrBlank(monthlyGrossSalary));
        row.put("NHF", moneyOrBlank(amountFrom(
                paymentInfo,
                MapKeys.NATIONAL_HOUSING_FUND,
                "National Housing Fund",
                "NHF"
        )));
        row.put("EMPLOYEE PENSION", moneyOrBlank(amountFrom(
                paymentInfo,
                MapKeys.EMPLOYEE_PENSION_CONTRIBUTION,
                "Employee Pension Contribution",
                "EMPLOYEE PENSION"
        )));
        row.put("VOLUNTARY PENSION CONTRIBUTION", moneyOrBlank(amountFrom(
                paymentInfo,
                "Voluntary Pension Contribution",
                "VOLUNTARY PENSION CONTRIBUTION"
        )));
        row.put("LIFE INSURANCE PREMIUM", moneyOrBlank(amountFrom(
                paymentInfo,
                "Life Insurance Premium",
                "Life Insurance",
                "LIFE INSURANCE PREMIUM"
        )));
        row.put("MORTGAGE INTEREST", moneyOrBlank(amountFrom(
                paymentInfo,
                "Mortgage Interest",
                "Mortgage",
                "MORTGAGE INTEREST"
        )));
        row.put("RENT RELIEF", moneyOrBlank(amountFrom(paymentInfo, "RENT RELIEF", "Rent Relief")));
        row.put("OTHER RELIEF", moneyOrBlank(amountFrom(
                paymentInfo,
                "Other Relief (If Applicable)",
                "Other Relief",
                "Custom Tax Relief",
                "CUSTOM TAX RELIEF"
        )));
        row.put("TOTAL TAX RELIEF", moneyOrBlank(amountFrom(
                paymentInfo,
                MapKeys.TOTAL_TAX_RELIEF,
                "Total Tax Relief",
                "MONTHLY RELIEF"
        )));
        row.put("TAXABLE GROSS", moneyOrBlank(amountFrom(
                paymentInfo,
                "MONTHLY CHARGEABLE INCOME",
                MapKeys.TAXABLE_INCOME,
                "TAXABLE INCOME",
                "TAXABLE GROSS"
        )));
        row.put("PAYE", moneyOrBlank(amountFrom(paymentInfo, "Total PAYE", "PAYE")));
        row.put("TOTAL PAYE", moneyOrBlank(amountFrom(paymentInfo, "Total PAYE", "PAYE")));

        return row;
    }

    private static Object moneyOrBlank(BigDecimal value) {
        return value != null ? value : " ";
    }

    private static BigDecimal amountFrom(PaymentInfo paymentInfo, String... keys) {
        if (paymentInfo == null) {
            return null;
        }
        BigDecimal value = mapDecimalIgnoreCase(paymentInfo.getGrossPay(), keys);
        if (value != null) return value;
        value = mapDecimalIgnoreCase(paymentInfo.getDeduction(), keys);
        if (value != null) return value;
        value = mapDecimalIgnoreCase(paymentInfo.getTaxRelief(), keys);
        if (value != null) return value;
        value = mapDecimalIgnoreCase(paymentInfo.getPension(), keys);
        if (value != null) return value;
        value = mapDecimalIgnoreCase(paymentInfo.getNhf(), keys);
        if (value != null) return value;
        value = mapDecimalIgnoreCase(paymentInfo.getPayeeTax(), keys);
        if (value != null) return value;
        return mapDecimalIgnoreCase(paymentInfo.getOthers(), keys);
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
            String hireDate = employeeDetail.getHireDate();
            String rawExit = employeeDetail.getExitDate();
            String exitDate = (rawExit != null && rawExit.equals(hireDate)) ? "" : rawExit;

            result.put("EMP ID", employeeDetail.getMappedId());
            result.put("EMPLOYEE NAME", paymentInfo.getFullName());
            result.put("HIRE DATE", AppUtil.formatDate(hireDate));
            result.put("EXIT DATE", AppUtil.formatDate(exitDate));
            result.put("ROLE", employeeDetail.getRole());
            result.put("DATE OF BIRTH", safeFormatDate(employeeDetail.getDateOfBirth()));
            result.put("SEX", employeeDetail.getSex() != null ? employeeDetail.getSex() : "");
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

        BigDecimal grossPayValue = mapDecimal(paymentInfo.getGrossPay(), "Gross Pay", MapKeys.GROSS_PAY);
        if (grossPayValue != null) {
            finalResult.put("NSITF", grossPayValue.multiply(new BigDecimal("0.01")));
        }

        Map<String, BigDecimal> ytd = paymentInfo.getYtdReport();
        if (ytd != null && !ytd.isEmpty()) {
            BigDecimal ytdGross = mapDecimal(ytd, "Gross Pay", MapKeys.GROSS_PAY);
            BigDecimal ytdEmployeePension = mapDecimal(
                    ytd,
                    "Employee Pension Contribution",
                    MapKeys.EMPLOYEE_PENSION_CONTRIBUTION
            );
            BigDecimal ytdEmployerPension = mapDecimal(
                    ytd,
                    "Employer Pension Contribution",
                    MapKeys.EMPLOYER_PENSION_CONTRIBUTION
            );
            if (ytdGross != null) {
                finalResult.put("GROSS SALARY (YTD)", ytdGross);
                BigDecimal ytdNsitf = ytdGross.multiply(new BigDecimal("0.01"));
                BigDecimal employerPension = ytdEmployerPension != null ? ytdEmployerPension : BigDecimal.ZERO;
                finalResult.put("ITF", employerPension.add(ytdNsitf).multiply(new BigDecimal("0.01")));
            }
            if (ytdEmployeePension != null) {
                finalResult.put("YTD EMPLOYEE PENSION @ 8%", ytdEmployeePension);
            }
        }

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
                .filter(e -> deductionComponents.contains(e.getKey().trim()))  // not in base list
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

                    if (isSerialNumberHeader(headerName) && value instanceof Number number) {
                        cell.setCellValue(number.longValue());
                    } else if (value instanceof Number number) {
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
                case "MONTHLY CHARGEABLE INCOME" -> newKey = "TAXABLE INCOME";
                case "Loan", "Loan." -> newKey = "LOAN DEDUCTION";
                case "other deduction" -> newKey = "OTHER DEDUCTION";
                case "Total PAYE" -> newKey = "PAYE";
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

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static boolean isSerialNumberHeader(String header) {
        if (header == null) {
            return false;
        }
        String normalized = header.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("serial number") || normalized.equals("s/n") || normalized.equals("sn");
    }

    private static Object resolveColumnValue(Map<String, Object> row, String header) {
        if (row == null || header == null) {
            return " ";
        }
        Object direct = row.get(header);
        if (hasValue(direct)) {
            return direct;
        }
        for (String alias : columnAliases(header)) {
            Object value = row.get(alias);
            if (hasValue(value)) {
                return value;
            }
        }
        return row.getOrDefault(header, " ");
    }

    private static boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            return !s.isBlank();
        }
        return true;
    }

    private static List<String> columnAliases(String header) {
        return switch (header) {
            case "Employee Code" -> List.of("EMP ID", "EMPLOYEE CODE", "EmployeeId");
            case "Employee name" -> List.of("EMPLOYEE NAME", "FULL NAME", "EmployeeName");
            case "Employee Role", "Employee role" -> List.of("ROLE");
            case "State of Residence" -> List.of("STATE OF RESIDENCE");
            case "Tax ID" -> List.of("TAX ID", "TAXID", "TIN");
            case "Gross Salary (Monthly)" -> List.of("GROSS SALARY (MONTHLY)", "GROSS SALARY");
            case "Monthly NHF" -> List.of("NHF", "MONTHLY NHF", "NATIONAL HOUSING FUND");
            case "Monthly Employee Pension @ 8%" -> List.of("EMPLOYEE PENSION", "MONTHLY EMPLOYEE PENSION @ 8%");
            case "Monthly Voluntary Pension" -> List.of("VOLUNTARY PENSION CONTRIBUTION", "MONTHLY VOLUNTARY PENSION");
            case "Life Insurance Premium" -> List.of("LIFE INSURANCE PREMIUM", "LIFE INSURANCE");
            case "Mortgage Interest" -> List.of("MORTGAGE INTEREST", "MORTGAGE");
            case "Rent Relief" -> List.of("RENT RELIEF");
            case "Other Relief (If Applicable)" -> List.of("OTHER RELIEF", "CUSTOM TAX RELIEF");
            case "Total Tax Relief" -> List.of("TOTAL TAX RELIEF", "MONTHLY RELIEF");
            case "Taxable Gross" -> List.of("TAXABLE GROSS", "MONTHLY CHARGEABLE INCOME", "TAXABLE INCOME");
            case "Total PAYE" -> List.of("TOTAL PAYE", "PAYE");
            case "Date of birth" -> List.of("DATE OF BIRTH");
            case "Sex" -> List.of("SEX");
            case "Gross Income" -> List.of("GROSS PAY", "GROSS INCOME");
            case "NSITF Amount" -> List.of("NSITF", "NSITF AMOUNT");
            case "Gross Salary (YTD)" -> List.of("GROSS SALARY (YTD)", "YTD GROSS PAY");
            case "YTD Employee Pension @ 8%" -> List.of(
                    "YTD EMPLOYEE PENSION @ 8%",
                    "YTD EMPLOYEE PENSION CONTRIBUTION"
            );
            case "ITF (Based on YTD Figures)" -> List.of("ITF", "ITF (BASED ON YTD FIGURES)");
            case "Voluntary Pension Contribution" -> List.of("VOLUNTARY PENSION CONTRIBUTION");
            case "Taxable Income" -> List.of("TAXABLE INCOME", "TAXABLE GROSS", "MONTHLY CHARGEABLE INCOME");
            case "PAYE Tax" -> List.of("PAYE TAX", "PAYE", "TOTAL PAYE");
            case "Net Pay" -> List.of("NETPAY", "NET PAY");
            case "Employer Pension" -> List.of("EMPLOYER PENSION");
            case "Deductions" -> List.of("DEDUCTIONS", "TOTAL DEDUCTION", "TOTAL DEDUCTIONS");
            default -> List.of(header.toUpperCase(Locale.ROOT));
        };
    }

    private static BigDecimal mapDecimal(Map<String, BigDecimal> values, String... keys) {
        if (values == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key != null && values.get(key) != null) {
                return values.get(key);
            }
        }
        return null;
    }

    private static BigDecimal mapDecimalIgnoreCase(Map<String, BigDecimal> values, String... keys) {
        BigDecimal exact = mapDecimal(values, keys);
        if (exact != null || values == null || keys == null) {
            return exact;
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            for (Map.Entry<String, BigDecimal> entry : values.entrySet()) {
                if (entry.getKey() != null
                        && entry.getKey().equalsIgnoreCase(key)
                        && entry.getValue() != null) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static String formatDateOfBirth(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return "";
        }
        try {
            LocalDate date = LocalDate.parse(dateStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (DateTimeParseException e) {
            return dateStr;
        }
    }

    private static String safeFormatDate(String dateStr) {
        try {
            String formatted = AppUtil.formatDate(dateStr);
            return formatted != null ? formatted : "";
        } catch (Exception e) {
            return dateStr != null ? dateStr : "";
        }
    }

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

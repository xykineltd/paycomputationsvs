package com.xykine.computation.reconciliation.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykine.computation.dto.EmployeeDetail;
import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.repo.PayrollReportDetailRepo;
import com.xykine.computation.request.EmployeeFilterRequest;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.reconciliation.mapping.ReconciliationColumnMapping;
import com.xykine.computation.reconciliation.mapping.ReconciliationMapping;
import com.xykine.computation.reconciliation.mapping.ReconciliationMappingReadiness;
import com.xykine.computation.reconciliation.mapping.ReconciliationMappingService;
import com.xykine.computation.reconciliation.mapping.ReconciliationTolerances;
import com.xykine.computation.service.AdminService;
import com.xykine.computation.service.ReportGeneratorService;
import com.xykine.computation.utils.ReportUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PayrollReconciliationRunService {

    private static final Logger log = LoggerFactory.getLogger(PayrollReconciliationRunService.class);

    private final ReconciliationMappingService mappingService;
    private final ReconciliationExcelParser excelParser;
    private final PayrollReconciliationTempRepository tempRepository;
    private final PayrollReconciliationTempRowRepository tempRowRepository;
    private final PayrollReconciliationDiffRepository diffRepository;
    private final PayrollReportDetailRepo payrollReportDetailRepo;
    private final AdminService adminService;
    private final ReportGeneratorService reportGeneratorService;
    private final ObjectMapper objectMapper;

    public PayrollReconciliationRunService(
            ReconciliationMappingService mappingService,
            ReconciliationExcelParser excelParser,
            PayrollReconciliationTempRepository tempRepository,
            PayrollReconciliationTempRowRepository tempRowRepository,
            PayrollReconciliationDiffRepository diffRepository,
            PayrollReportDetailRepo payrollReportDetailRepo,
            AdminService adminService,
            ReportGeneratorService reportGeneratorService,
            ObjectMapper objectMapper
    ) {
        this.mappingService = mappingService;
        this.excelParser = excelParser;
        this.tempRepository = tempRepository;
        this.tempRowRepository = tempRowRepository;
        this.diffRepository = diffRepository;
        this.payrollReportDetailRepo = payrollReportDetailRepo;
        this.adminService = adminService;
        this.reportGeneratorService = reportGeneratorService;
        this.objectMapper = objectMapper;
    }

    /**
     * Upload Excel → complete-replace temp raw rows → run input alignment.
     * Loads aliases from reconciliationMappings by companyId; optional legalEntityId picks among them.
     */
    @Transactional
    public StageRunResponse runInputAlignment(
            String companyId,
            String reportId,
            String legalEntityId,
            MultipartFile file,
            String bearerToken
    ) {
        require(companyId, "companyId");
        require(reportId, "reportId");
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required");
        }

        ReconciliationMapping mapping = mappingService.getByCompanyId(companyId);
        if (mapping.getId() == null) {
            throw new IllegalArgumentException(
                    "No reconciliation mapping saved for companyId=" + companyId
                            + ". Configure and save it under Reconciliation Mapping first "
                            + "(Mongo collection: reconciliationMappings).");
        }
        ReconciliationMappingReadiness.ReadinessResult readiness = ReconciliationMappingReadiness.evaluate(mapping);
        if (!readiness.ready()) {
            throw new IllegalArgumentException(
                    "Reconciliation mapping is incomplete: " + String.join(", ", readiness.missing()));
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read uploaded file", e);
        }

        ReconciliationExcelParser.ParsedSheet parsed =
                excelParser.parse(bytes, mapping, companyId, legalEntityId);

        // Complete replacement for this company/report scope
        replaceExistingTemp(companyId, reportId);

        Instant now = Instant.now();
        PayrollReconciliationTemp run = PayrollReconciliationTemp.builder()
                .companyId(companyId)
                .reportId(reportId)
                .legalEntityId(legalEntityId)
                .fileName(file.getOriginalFilename())
                .sheetName(parsed.sheetName())
                .rowCount(parsed.rows().size())
                .headerRowIndex(mapping.getHeaderRowIndex())
                .dataStartRow(mapping.getDataStartRow())
                .excelMatchKey(mapping.getExcelMatchKey())
                .systemMatchKey(mapping.getSystemMatchKey())
                .tolerances(toToleranceMap(mapping.getTolerances()))
                .columnMappings(snapshotColumns(mapping))
                .status("UPLOADED")
                .createdAt(now)
                .updatedAt(now)
                .build();
        run = tempRepository.save(run);

        List<PayrollReconciliationTempRow> rows = new ArrayList<>();
        for (PayrollReconciliationTempRow row : parsed.rows()) {
            row.setId(null);
            row.setReconciliationId(run.getId());
            row.setCompanyId(companyId);
            row.setReportId(reportId);
            rows.add(row);
        }
        if (!rows.isEmpty()) {
            tempRowRepository.saveAll(rows);
        }
        log.info("Stored {} raw Excel rows for reconciliationId={}", rows.size(), run.getId());

        return executeStage(run, mapping, rows, "input", bearerToken);
    }

    @Transactional
    public StageRunResponse runOutcomeVariance(String reconciliationId, String bearerToken) {
        PayrollReconciliationTemp run = getRun(reconciliationId);
        if (!Boolean.TRUE.equals(run.getInputPassed())) {
            throw new IllegalArgumentException(
                    "Outcome Variance is blocked until Input Alignment passes");
        }

        ReconciliationMapping mapping = mappingService.getByCompanyId(run.getCompanyId());
        List<PayrollReconciliationTempRow> rows = tempRowRepository.findByReconciliationId(reconciliationId);
        if (rows.isEmpty()) {
            throw new IllegalStateException("No raw uploaded rows found for reconciliationId=" + reconciliationId);
        }

        diffRepository.deleteByReconciliationIdAndStage(reconciliationId, "outcome");
        return executeStage(run, mapping, rows, "outcome", bearerToken);
    }

    public ReconciliationAnalyticsResponse getAnalytics(String reconciliationId) {
        PayrollReconciliationTemp run = getRun(reconciliationId);
        return ReconciliationAnalyticsResponse.builder()
                .reconciliationId(run.getId())
                .status(run.getStatus())
                .inputPassed(run.getInputPassed())
                .outcomePassed(run.getOutcomePassed())
                .input(run.getInputAnalytics())
                .outcome(run.getOutcomeAnalytics())
                .build();
    }

    public ReconciliationDetailsResponse getDetails(
            String reconciliationId, String stage, String status, int page, int size
    ) {
        getRun(reconciliationId);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        Page<PayrollReconciliationDiff> result;
        if (status != null && !status.isBlank()) {
            result = diffRepository.findByReconciliationIdAndStageAndStatus(
                    reconciliationId, stage, status, pageable);
        } else {
            result = diffRepository.findByReconciliationIdAndStage(reconciliationId, stage, pageable);
        }
        return ReconciliationDetailsResponse.builder()
                .details(result.getContent())
                .totalItems(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .currentPage(result.getNumber())
                .build();
    }

    private StageRunResponse executeStage(
            PayrollReconciliationTemp run,
            ReconciliationMapping mapping,
            List<PayrollReconciliationTempRow> excelRows,
            String stage,
            String bearerToken
    ) {
        List<ReconciliationColumnMapping> columns = compareColumns(mapping, stage);
        Map<String, Map<String, Object>> systemByCode = loadSystemRows(run, bearerToken);

        Map<String, PayrollReconciliationTempRow> excelByCode = new LinkedHashMap<>();
        for (PayrollReconciliationTempRow row : excelRows) {
            String code = ReconciliationValueSupport.normalizeEmpId(row.getMatchKeyValue());
            if (!code.isBlank()) {
                excelByCode.putIfAbsent(code, row);
            }
        }

        List<PayrollReconciliationDiff> diffs = new ArrayList<>();
        Set<String> matchedCodes = new HashSet<>();

        long matchedEmployees = 0;
        long mismatchedEmployees = 0;
        long excelOnly = 0;
        long systemOnly = 0;
        long hardFailures = 0;

        for (Map.Entry<String, PayrollReconciliationTempRow> entry : excelByCode.entrySet()) {
            String code = entry.getKey();
            PayrollReconciliationTempRow excelRow = entry.getValue();
            Map<String, Object> systemRow = systemByCode.get(code);

            if (systemRow == null) {
                excelOnly++;
                hardFailures++;
                diffs.add(PayrollReconciliationDiff.builder()
                        .reconciliationId(run.getId())
                        .stage(stage)
                        .status("EXCEL_ONLY")
                        .employeeCode(code)
                        .employeeName(stringCell(excelRow, "EMPLOYEE NAME"))
                        .field(mapping.getExcelMatchKey())
                        .excelValue(code)
                        .systemValue(null)
                        .valueType("text")
                        .severity("hard")
                        .build());
                continue;
            }

            matchedCodes.add(code);
            boolean employeeMismatch = false;
            String employeeName = employeeName(systemRow, excelRow);

            for (ReconciliationColumnMapping col : columns) {
                Object excelVal = ReconciliationExcelParser.cellForHeader(excelRow.getCells(), col.getExcelHeader());
                Object systemVal = ReconciliationValueSupport.lookupSystemValue(
                        systemRow, col.getExcelHeader(), col.getSystemPath());
                boolean equal = ReconciliationValueSupport.valuesEqual(
                        excelVal, systemVal, col.getValueType(), mapping.getTolerances());
                if (equal) {
                    continue;
                }
                employeeMismatch = true;
                String severity = col.getSeverity() != null ? col.getSeverity() : "soft";
                if ("hard".equalsIgnoreCase(severity)) {
                    hardFailures++;
                }
                diffs.add(PayrollReconciliationDiff.builder()
                        .reconciliationId(run.getId())
                        .stage(stage)
                        .status("MISMATCH")
                        .employeeCode(code)
                        .employeeName(employeeName)
                        .field(col.getExcelHeader())
                        .excelValue(excelVal)
                        .systemValue(systemVal)
                        .valueType(col.getValueType())
                        .delta(ReconciliationValueSupport.delta(excelVal, systemVal, col.getValueType()))
                        .severity(severity)
                        .build());
            }

            if (employeeMismatch) {
                mismatchedEmployees++;
            } else {
                matchedEmployees++;
            }
        }

        for (Map.Entry<String, Map<String, Object>> entry : systemByCode.entrySet()) {
            if (matchedCodes.contains(entry.getKey()) || excelByCode.containsKey(entry.getKey())) {
                continue;
            }
            systemOnly++;
            hardFailures++;
            Map<String, Object> systemRow = entry.getValue();
            diffs.add(PayrollReconciliationDiff.builder()
                    .reconciliationId(run.getId())
                    .stage(stage)
                    .status("SYSTEM_ONLY")
                    .employeeCode(entry.getKey())
                    .employeeName(employeeName(systemRow, null))
                    .field(mapping.getSystemMatchKey())
                    .excelValue(null)
                    .systemValue(entry.getKey())
                    .valueType("text")
                    .severity("hard")
                    .build());
        }

        if (!diffs.isEmpty()) {
            diffRepository.saveAll(diffs);
        }

        PayrollReconciliationTemp.StageAnalytics analytics = PayrollReconciliationTemp.StageAnalytics.builder()
                .matched(matchedEmployees)
                .mismatched(mismatchedEmployees)
                .excelOnly(excelOnly)
                .systemOnly(systemOnly)
                .hardFailures(hardFailures)
                .totalDiffRows(diffs.size())
                .build();

        boolean passed = hardFailures == 0;
        Instant now = Instant.now();
        run.setUpdatedAt(now);
        if ("input".equals(stage)) {
            run.setInputAnalytics(analytics);
            run.setInputPassed(passed);
            run.setStatus("INPUT_DONE");
            run.setOutcomeAnalytics(null);
            run.setOutcomePassed(null);
        } else {
            run.setOutcomeAnalytics(analytics);
            run.setOutcomePassed(passed);
            run.setStatus("OUTCOME_DONE");
        }
        tempRepository.save(run);

        return StageRunResponse.builder()
                .reconciliationId(run.getId())
                .sheetName(run.getSheetName())
                .rowCount(run.getRowCount())
                .passed(passed)
                .summary(analytics)
                .status(run.getStatus())
                .build();
    }

    private Map<String, Map<String, Object>> loadSystemRows(PayrollReconciliationTemp run, String bearerToken) {
        List<Map<String, Object>> reportRows = reportGeneratorService.loadPaymentInfoRowsForReport(
                run.getCompanyId(), run.getReportId(), bearerToken);
        Map<String, Map<String, Object>> byCodeOut = new LinkedHashMap<>();
        if (reportRows != null) {
            for (Map<String, Object> row : reportRows) {
                if (row == null) {
                    continue;
                }
                String code = employeeCodeFromReportRow(row);
                if (code.isBlank()) {
                    continue;
                }
                byCodeOut.put(code, row);
            }
        }

        if (byCodeOut.isEmpty()) {
            byCodeOut.putAll(loadFlattenedSystemRows(run, bearerToken));
        }

        log.info("Loaded {} system rows for reconciliation companyId={} reportId={}",
                byCodeOut.size(), run.getCompanyId(), run.getReportId());
        return byCodeOut;
    }

    private Map<String, Map<String, Object>> loadFlattenedSystemRows(PayrollReconciliationTemp run, String bearerToken) {
        List<PayrollReportDetail> details =
                payrollReportDetailRepo.findPayrollReportDetailByCompanyIdAndSummaryId(
                        run.getCompanyId(), run.getReportId());
        List<ReportResponse> reports = details.isEmpty()
                ? List.of()
                : ReportUtils.transform(details);

        Map<String, EmployeeDetail> byEmployeeId = loadEmployeeDetails(run, bearerToken);
        Map<String, Map<String, Object>> byCodeOut = new LinkedHashMap<>();
        for (ReportResponse report : reports) {
            EmployeeDetail emp = byEmployeeId.get(report.getEmployeeId());
            Map<String, Object> flat = ReconciliationValueSupport.flattenSystemRow(report, emp);
            Object codeObj = flat.get("employeeCode");
            if (codeObj == null || String.valueOf(codeObj).isBlank()) {
                continue;
            }
            byCodeOut.put(ReconciliationValueSupport.normalizeEmpId(String.valueOf(codeObj)), flat);
        }
        return byCodeOut;
    }

    private static String employeeCodeFromReportRow(Map<String, Object> row) {
        for (String key : List.of("EMP ID", "EMPID", "employeeCode", "employeeID", "employeeId")) {
            Object v = row.get(key);
            if (v != null && !String.valueOf(v).isBlank()) {
                return ReconciliationValueSupport.normalizeEmpId(String.valueOf(v));
            }
        }
        Object lookedUp = ReconciliationValueSupport.lookupSystemValue(row, "EMP ID", "EMP ID");
        return lookedUp == null ? "" : ReconciliationValueSupport.normalizeEmpId(String.valueOf(lookedUp));
    }

    private List<ReconciliationColumnMapping> compareColumns(ReconciliationMapping mapping, String stage) {
        String matchKey = mapping.getExcelMatchKey();
        return ReconciliationExcelParser.enabledColumns(mapping, stage).stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsMatchKey()))
                .filter(c -> matchKey == null
                        || !ReconciliationExcelParser.normalizeHeader(c.getExcelHeader())
                        .equals(ReconciliationExcelParser.normalizeHeader(matchKey)))
                .toList();
    }

    private Map<String, EmployeeDetail> loadEmployeeDetails(PayrollReconciliationTemp run, String bearerToken) {
        try {
            EmployeeFilterRequest filter = new EmployeeFilterRequest();
            filter.setCompanyID(run.getCompanyId());
            filter.setReportId(run.getReportId());
            filter.setPage(0);
            filter.setSize(10_000);
            Map<String, EmployeeDetail> details = adminService.getEmployeesDetail(filter, bearerToken);
            return details != null ? details : Collections.emptyMap();
        } catch (Exception ex) {
            log.warn("Unable to load employee details for companyId={}: {}", run.getCompanyId(), ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private void replaceExistingTemp(String companyId, String reportId) {
        List<PayrollReconciliationTemp> existing =
                tempRepository.findByCompanyIdAndReportId(companyId, reportId);
        if (existing.isEmpty()) {
            return;
        }
        List<String> ids = existing.stream().map(PayrollReconciliationTemp::getId).toList();
        tempRowRepository.deleteByReconciliationIdIn(ids);
        diffRepository.deleteByReconciliationIdIn(ids);
        tempRepository.deleteAll(existing);
        log.info("Replaced {} prior reconciliation temp run(s) for companyId={} reportId={}",
                ids.size(), companyId, reportId);
    }

    private PayrollReconciliationTemp getRun(String reconciliationId) {
        return tempRepository.findById(reconciliationId)
                .orElseThrow(() -> new PayrollReconciliationNotFoundException(reconciliationId));
    }

    private Map<String, Double> toToleranceMap(ReconciliationTolerances tolerances) {
        Map<String, Double> map = new HashMap<>();
        if (tolerances == null) {
            map.put("money", 0.01);
            map.put("days", 0.0);
            map.put("factor", 0.0001);
            return map;
        }
        map.put("money", tolerances.getMoney());
        map.put("days", tolerances.getDays());
        map.put("factor", tolerances.getFactor());
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> snapshotColumns(ReconciliationMapping mapping) {
        if (mapping.getColumnMappings() == null) {
            return List.of();
        }
        return mapping.getColumnMappings().stream()
                .map(c -> objectMapper.convertValue(c, Map.class))
                .map(m -> (Map<String, Object>) m)
                .collect(Collectors.toList());
    }

    private static String employeeName(Map<String, Object> systemRow, PayrollReconciliationTempRow excelRow) {
        Object name = ReconciliationValueSupport.lookupSystemValue(systemRow, "EMPLOYEE NAME", "EMPLOYEE NAME");
        if (!ReconciliationValueSupport.isAbsent(name)) {
            return String.valueOf(name);
        }
        Object fullName = systemRow != null ? systemRow.get("fullName") : null;
        if (!ReconciliationValueSupport.isAbsent(fullName)) {
            return String.valueOf(fullName);
        }
        return excelRow == null ? null : stringCell(excelRow, "EMPLOYEE NAME");
    }

    private static String stringCell(PayrollReconciliationTempRow row, String header) {
        Object v = ReconciliationExcelParser.cellForHeader(row.getCells(), header);
        return v == null ? null : String.valueOf(v);
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}

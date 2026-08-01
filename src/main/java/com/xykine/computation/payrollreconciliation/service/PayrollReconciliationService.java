package com.xykine.computation.payrollreconciliation.service;

import com.xykine.computation.dto.EmployeeDetail;
import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.payrollreconciliation.defaults.DefaultReconciliationMapping;
import com.xykine.computation.payrollreconciliation.dto.*;
import com.xykine.computation.payrollreconciliation.entity.*;
import com.xykine.computation.payrollreconciliation.parser.ReconciliationExcelParser;
import com.xykine.computation.payrollreconciliation.repo.*;
import com.xykine.computation.repo.PayrollReportDetailRepo;
import com.xykine.computation.request.EmployeeFilterRequest;
import com.xykine.computation.response.PayComputeDetailResponse;
import com.xykine.computation.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xykine.payroll.model.PaymentInfo;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayrollReconciliationService {

    private final ReconciliationMappingRepo mappingRepo;
    private final ReconciliationRunRepo runRepo;
    private final ReconciliationExcelRowRepo excelRowRepo;
    private final ReconciliationDiffRepo diffRepo;
    private final PayrollReportDetailRepo payrollReportDetailRepo;
    private final ReconciliationExcelParser excelParser;
    private final AdminService adminService;

    public ReconciliationMapping getMapping(String companyId) {
        return mappingRepo.findByCompanyId(companyId)
                .orElseGet(() -> DefaultReconciliationMapping.create(companyId, new ArrayList<>()));
    }

    public ReconciliationMapping saveMapping(String companyId, ReconciliationMapping body) {
        ReconciliationMapping existing = mappingRepo.findByCompanyId(companyId).orElse(null);
        ReconciliationMapping toSave = body != null ? body : DefaultReconciliationMapping.create(companyId, new ArrayList<>());
        toSave.setCompanyId(companyId);
        if (existing != null) {
            toSave.setId(existing.getId());
        }
        MappingStatusResponse status = evaluateReadiness(toSave);
        toSave.setStatus(status.getStatus());
        toSave.setUpdatedAt(LocalDateTime.now());
        return mappingRepo.save(toSave);
    }

    public MappingStatusResponse getMappingStatus(String companyId) {
        return evaluateReadiness(getMapping(companyId));
    }

    public MappingStatusResponse evaluateReadiness(ReconciliationMapping mapping) {
        List<String> missing = new ArrayList<>();
        if (mapping == null) {
            return MappingStatusResponse.builder().ready(false).status("INCOMPLETE").missing(List.of("Mapping profile is missing")).build();
        }
        if (mapping.getHeaderRowIndex() < 1) missing.add("Header row index");
        if (mapping.getDataStartRow() < 1) missing.add("Data start row");
        if (isBlank(mapping.getExcelMatchKey())) missing.add("Excel employee match key");
        if (isBlank(mapping.getSystemMatchKey())) missing.add("System employee match key");

        List<EntityAlias> aliases = mapping.getEntityAliases() != null ? mapping.getEntityAliases() : List.of();
        boolean hasAlias = aliases.stream().anyMatch(a ->
                a != null && !isBlank(a.getLegalEntityId())
                        && (!isBlank(a.getExcelSheetName()) || !isBlank(a.getExcelLegalEntityValue())));
        if (!hasAlias) missing.add("At least one entity ↔ Excel sheet alias");

        List<ColumnMapping> cols = mapping.getColumnMappings() != null ? mapping.getColumnMappings() : List.of();
        boolean matchMapped = cols.stream().anyMatch(c -> c.isEnabled()
                && (c.isMatchKey() || normalize(c.getExcelHeader()).equals(normalize(mapping.getExcelMatchKey())))
                && !isBlank(c.getSystemPath()));
        if (!matchMapped) missing.add("Match key column mapping");

        long hardInputs = cols.stream().filter(c -> c.isEnabled() && "input".equalsIgnoreCase(c.getStage()) && "hard".equalsIgnoreCase(c.getSeverity())).count();
        long hardOutcomes = cols.stream().filter(c -> c.isEnabled() && "outcome".equalsIgnoreCase(c.getStage()) && "hard".equalsIgnoreCase(c.getSeverity())).count();
        if (hardInputs == 0) missing.add("At least one hard input column mapping");
        if (hardOutcomes == 0) missing.add("At least one hard outcome column mapping");

        boolean ready = missing.isEmpty();
        return MappingStatusResponse.builder()
                .ready(ready)
                .status(ready ? "READY" : "INCOMPLETE")
                .missing(missing)
                .build();
    }

    public UploadResponse upload(String companyId, String reportId, String legalEntityId, MultipartFile file) {
        MappingStatusResponse status = getMappingStatus(companyId);
        if (!status.isReady()) {
            throw new IllegalArgumentException("Reconciliation mapping is incomplete: " + String.join(", ", status.getMissing()));
        }
        ReconciliationMapping mapping = getMapping(companyId);
        EntityAlias alias = (mapping.getEntityAliases() != null ? mapping.getEntityAliases() : List.<EntityAlias>of())
                .stream()
                .filter(a -> Objects.equals(String.valueOf(a.getLegalEntityId()), String.valueOf(legalEntityId)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected legal entity has no Excel sheet alias in Reconciliation Mapping."));

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read uploaded file");
        }

        ReconciliationExcelParser.ParseResult parsed = excelParser.parse(bytes, mapping, alias);

        ReconciliationRun run = ReconciliationRun.builder()
                .companyId(companyId)
                .reportId(reportId)
                .legalEntityId(legalEntityId)
                .legalEntityName(alias.getLegalEntityName())
                .sheetName(parsed.sheetName())
                .status("UPLOADED")
                .inputPassed(false)
                .outcomePassed(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        final ReconciliationRun savedRun = runRepo.save(run);

        List<ReconciliationExcelRow> rows = parsed.rows().stream()
                .peek(r -> {
                    r.setId(null);
                    r.setRunId(savedRun.getId());
                    r.setCompanyId(companyId);
                })
                .toList();
        excelRowRepo.saveAll(rows);

        return UploadResponse.builder()
                .reconciliationId(savedRun.getId())
                .sheetName(parsed.sheetName())
                .rowCount(rows.size())
                .legalEntityId(legalEntityId)
                .legalEntityName(alias.getLegalEntityName())
                .build();
    }

    public StageResultResponse runInputAlignment(String runId, String authorizationHeader) {
        ReconciliationRun run = requireRun(runId);
        ReconciliationMapping mapping = requireReadyMapping(run.getCompanyId());
        List<ReconciliationExcelRow> excelRows = excelRowRepo.findByRunId(runId);
        Map<String, SystemSnapshot> systemByCode = loadSystemSnapshots(run, authorizationHeader);

        diffRepo.deleteByRunIdAndStage(runId, "input");

        List<ColumnMapping> inputCols = enabledColumns(mapping, "input").stream()
                .filter(c -> !c.isMatchKey())
                .filter(c -> !normalize(c.getExcelHeader()).equals(normalize(mapping.getExcelMatchKey())))
                .toList();

        List<ReconciliationDiff> diffs = new ArrayList<>();
        Set<String> excelCodes = new HashSet<>();
        Map<String, String> empNames = new HashMap<>();

        for (ReconciliationExcelRow row : excelRows) {
            String code = upper(row.getEmployeeCode());
            excelCodes.add(code);
            empNames.put(code, row.getEmployeeName());
            SystemSnapshot system = systemByCode.get(code);
            if (system == null) {
                diffs.add(diff(run, "input", code, row.getEmployeeName(), "EXCEL_ONLY",
                        mapping.getExcelMatchKey(), mapping.getSystemMatchKey(), code, "", null, "hard", "text"));
                continue;
            }
            for (ColumnMapping col : inputCols) {
                String excelVal = valueFromExcel(row, col.getExcelHeader());
                String systemVal = valueFromSystem(system, col.getSystemPath());
                boolean equal = valuesEqual(excelVal, systemVal, col.getValueType(), mapping.getTolerances());
                if (!equal) {
                    diffs.add(diff(run, "input", code, coalesce(row.getEmployeeName(), system.fullName),
                            "MISMATCH", col.getExcelHeader(), col.getSystemPath(), excelVal, systemVal,
                            delta(excelVal, systemVal, col.getValueType()), col.getSeverity(), col.getValueType()));
                }
            }
        }

        for (Map.Entry<String, SystemSnapshot> e : systemByCode.entrySet()) {
            if (excelCodes.contains(e.getKey())) continue;
            diffs.add(diff(run, "input", e.getKey(), e.getValue().fullName, "SYSTEM_ONLY",
                    mapping.getExcelMatchKey(), mapping.getSystemMatchKey(), "", e.getKey(), null, "hard", "text"));
        }

        if (!diffs.isEmpty()) {
            diffRepo.saveAll(diffs);
        }

        StageAnalytics analytics = buildInputAnalytics(excelCodes.size(), systemByCode.size(), diffs, excelCodes, systemByCode.keySet());
        boolean passed = analytics.getHardFailures() == 0;
        run.setInputAnalytics(analytics);
        run.setInputPassed(passed);
        run.setStatus("INPUT_DONE");
        run.setUpdatedAt(LocalDateTime.now());
        runRepo.save(run);

        return StageResultResponse.builder()
                .reconciliationId(runId)
                .stage("input")
                .passed(passed)
                .summary(analytics)
                .build();
    }

    public StageResultResponse runOutcomeVariance(String runId, String authorizationHeader) {
        ReconciliationRun run = requireRun(runId);
        if (!run.isInputPassed()) {
            throw new IllegalStateException("Outcome Variance is blocked until Input Alignment passes.");
        }
        ReconciliationMapping mapping = requireReadyMapping(run.getCompanyId());
        List<ReconciliationExcelRow> excelRows = excelRowRepo.findByRunId(runId);
        Map<String, SystemSnapshot> systemByCode = loadSystemSnapshots(run, authorizationHeader);

        diffRepo.deleteByRunIdAndStage(runId, "outcome");
        List<ColumnMapping> outcomeCols = enabledColumns(mapping, "outcome");
        List<ReconciliationDiff> diffs = new ArrayList<>();
        long compared = 0;
        long matchedEmployees = 0;
        long mismatchedEmployees = 0;

        for (ReconciliationExcelRow row : excelRows) {
            String code = upper(row.getEmployeeCode());
            SystemSnapshot system = systemByCode.get(code);
            if (system == null) continue;
            compared++;
            List<ReconciliationDiff> empDiffs = new ArrayList<>();
            for (ColumnMapping col : outcomeCols) {
                String excelVal = valueFromExcel(row, col.getExcelHeader());
                String systemVal = valueFromSystem(system, col.getSystemPath());
                boolean equal = valuesEqual(excelVal, systemVal, col.getValueType(), mapping.getTolerances());
                if (!equal) {
                    empDiffs.add(diff(run, "outcome", code, coalesce(row.getEmployeeName(), system.fullName),
                            "MISMATCH", col.getExcelHeader(), col.getSystemPath(), excelVal, systemVal,
                            delta(excelVal, systemVal, col.getValueType()), col.getSeverity(), col.getValueType()));
                }
            }
            if (empDiffs.isEmpty()) matchedEmployees++;
            else mismatchedEmployees++;
            diffs.addAll(empDiffs);
        }

        if (!diffs.isEmpty()) {
            diffRepo.saveAll(diffs);
        }

        long hardFailures = diffs.stream().filter(d -> "hard".equalsIgnoreCase(d.getSeverity())).count();
        StageAnalytics analytics = StageAnalytics.builder()
                .matched(matchedEmployees)
                .mismatched(mismatchedEmployees)
                .hardFailures(hardFailures)
                .totalCompared(compared)
                .totalDiffRows(diffs.size())
                .build();

        boolean passed = hardFailures == 0;
        run.setOutcomeAnalytics(analytics);
        run.setOutcomePassed(passed);
        run.setStatus("OUTCOME_DONE");
        run.setUpdatedAt(LocalDateTime.now());
        runRepo.save(run);

        return StageResultResponse.builder()
                .reconciliationId(runId)
                .stage("outcome")
                .passed(passed)
                .summary(analytics)
                .build();
    }

    public AnalyticsResponse getAnalytics(String runId) {
        ReconciliationRun run = requireRun(runId);
        return AnalyticsResponse.builder()
                .reconciliationId(run.getId())
                .status(run.getStatus())
                .inputPassed(run.isInputPassed())
                .outcomePassed(run.isOutcomePassed())
                .sheetName(run.getSheetName())
                .legalEntityId(run.getLegalEntityId())
                .legalEntityName(run.getLegalEntityName())
                .input(run.getInputAnalytics())
                .outcome(run.getOutcomeAnalytics())
                .build();
    }

    public PagedDetailsResponse getDetails(String runId, String stage, String status, int page, int size) {
        requireRun(runId);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        Page<ReconciliationDiff> result;
        if (!isBlank(status)) {
            result = diffRepo.findByRunIdAndStageAndStatus(runId, stage, status, pageable);
        } else {
            // default: show non-MATCH rows first by querying without MATCH filter — return all for stage
            result = diffRepo.findByRunIdAndStage(runId, stage, pageable);
        }
        return PagedDetailsResponse.builder()
                .details(result.getContent())
                .currentPage(result.getNumber())
                .totalItems(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    private StageAnalytics buildInputAnalytics(int totalExcel, int totalSystem, List<ReconciliationDiff> diffs,
                                               Set<String> excelCodes, Set<String> systemCodes) {
        Set<String> mismatched = diffs.stream().filter(d -> "MISMATCH".equals(d.getStatus())).map(ReconciliationDiff::getEmployeeCode).collect(Collectors.toSet());
        Set<String> excelOnly = diffs.stream().filter(d -> "EXCEL_ONLY".equals(d.getStatus())).map(ReconciliationDiff::getEmployeeCode).collect(Collectors.toSet());
        Set<String> systemOnly = diffs.stream().filter(d -> "SYSTEM_ONLY".equals(d.getStatus())).map(ReconciliationDiff::getEmployeeCode).collect(Collectors.toSet());
        long hardFailures = diffs.stream().filter(d -> "hard".equalsIgnoreCase(d.getSeverity())).count();
        long matched = excelCodes.stream().filter(c -> systemCodes.contains(c) && !mismatched.contains(c)).count();
        return StageAnalytics.builder()
                .matched(matched)
                .mismatched(mismatched.size())
                .excelOnly(excelOnly.size())
                .systemOnly(systemOnly.size())
                .hardFailures(hardFailures)
                .totalExcel(totalExcel)
                .totalSystem(totalSystem)
                .totalDiffRows(diffs.size())
                .build();
    }

    private Map<String, SystemSnapshot> loadSystemSnapshots(ReconciliationRun run, String authorizationHeader) {
        List<PayrollReportDetail> details = payrollReportDetailRepo
                .findPayrollReportDetailByCompanyIdAndSummaryId(run.getCompanyId(), run.getReportId());
        if (details == null) details = List.of();

        Map<String, EmployeeDetail> employeeDetails = Map.of();
        try {
            EmployeeFilterRequest req = new EmployeeFilterRequest();
            req.setCompanyID(run.getCompanyId());
            req.setReportId(run.getReportId());
            req.setEmployeeIds(details.stream().map(PayrollReportDetail::getEmployeeId).filter(Objects::nonNull).toList());
            if (!req.getEmployeeIds().isEmpty() && authorizationHeader != null) {
                employeeDetails = Optional.ofNullable(adminService.getEmployeesDetail(req, authorizationHeader)).orElse(Map.of());
            }
        } catch (Exception ignored) {
            employeeDetails = Map.of();
        }

        Map<String, SystemSnapshot> byCode = new HashMap<>();
        for (PayrollReportDetail detail : details) {
            PayComputeDetailResponse detailResp = null;
            try {
                detailResp = SerializationUtils.deserialize(detail.getReport());
            } catch (Exception ignored) {}
            PaymentInfo info = detailResp != null ? detailResp.getReport() : null;
            EmployeeDetail emp = employeeDetails.get(detail.getEmployeeId());
            String code = emp != null && !isBlank(emp.getMappedId())
                    ? upper(emp.getMappedId())
                    : upper(detail.getEmployeeId());
            SystemSnapshot snap = new SystemSnapshot();
            snap.employeeId = detail.getEmployeeId();
            snap.employeeCode = code;
            snap.fullName = coalesce(detail.getFullName(), emp != null ? emp.getName() : null, info != null ? info.getFullName() : null);
            snap.paymentInfo = info;
            snap.hireDate = emp != null ? emp.getHireDate() : null;
            snap.exitDate = emp != null ? emp.getExitDate() : null;
            snap.role = emp != null ? emp.getRole() : null;
            snap.legalEntityName = run.getLegalEntityName();
            byCode.put(code, snap);
        }
        return byCode;
    }

    private List<ColumnMapping> enabledColumns(ReconciliationMapping mapping, String stage) {
        return (mapping.getColumnMappings() != null ? mapping.getColumnMappings() : List.<ColumnMapping>of())
                .stream()
                .filter(ColumnMapping::isEnabled)
                .filter(c -> stage.equalsIgnoreCase(c.getStage()))
                .toList();
    }

    private ReconciliationDiff diff(ReconciliationRun run, String stage, String code, String name, String status,
                                    String field, String systemPath, String excel, String system, BigDecimal delta,
                                    String severity, String valueType) {
        return ReconciliationDiff.builder()
                .runId(run.getId())
                .companyId(run.getCompanyId())
                .stage(stage)
                .employeeCode(code)
                .employeeName(name)
                .status(status)
                .field(field)
                .systemPath(systemPath)
                .excelValue(excel)
                .systemValue(system)
                .delta(delta)
                .severity(severity != null ? severity : "soft")
                .valueType(valueType)
                .build();
    }

    private String valueFromExcel(ReconciliationExcelRow row, String header) {
        if (row.getValues() == null || header == null) return "";
        if (row.getValues().containsKey(header)) return nullToEmpty(row.getValues().get(header));
        String target = normalize(header);
        for (Map.Entry<String, String> e : row.getValues().entrySet()) {
            if (normalize(e.getKey()).equals(target)) return nullToEmpty(e.getValue());
        }
        return "";
    }

    private String valueFromSystem(SystemSnapshot snap, String path) {
        if (isBlank(path) || snap == null) return "";
        return switch (path) {
            case "employeeCode" -> nullToEmpty(snap.employeeCode);
            case "fullName" -> nullToEmpty(snap.fullName);
            case "legalEntityName" -> nullToEmpty(snap.legalEntityName);
            case "role" -> nullToEmpty(snap.role);
            case "employeeHireDate", "hireDate" -> nullToEmpty(snap.hireDate);
            case "exitDate" -> nullToEmpty(snap.exitDate);
            case "daysWorked" -> ""; // not always on PaymentInfo
            case "proratingFactor" -> "";
            case "numberOfDaysOfUnpaidAbsence" -> snap.paymentInfo != null
                    ? String.valueOf(snap.paymentInfo.getNumberOfDaysOfUnpaidAbsence()) : "";
            case "netPay" -> snap.paymentInfo != null && snap.paymentInfo.getNetPay() != null
                    ? snap.paymentInfo.getNetPay().toPlainString() : "";
            case "basicSalary" -> snap.paymentInfo != null && snap.paymentInfo.getBasicSalary() != null
                    ? snap.paymentInfo.getBasicSalary().toPlainString() : "";
            default -> resolvePaymentInfoPath(snap.paymentInfo, path);
        };
    }

    @SuppressWarnings("unchecked")
    private String resolvePaymentInfoPath(PaymentInfo info, String path) {
        if (info == null || isBlank(path)) return "";
        String[] parts = path.split("\\.", 2);
        try {
            Field field = PaymentInfo.class.getDeclaredField(parts[0]);
            field.setAccessible(true);
            Object val = field.get(info);
            if (parts.length == 1) {
                return val == null ? "" : String.valueOf(val);
            }
            if (val instanceof Map<?, ?> map) {
                Object nested = map.get(parts[1]);
                if (nested == null) {
                    // case-insensitive key fallback
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        if (String.valueOf(e.getKey()).equalsIgnoreCase(parts[1])) {
                            nested = e.getValue();
                            break;
                        }
                    }
                }
                return nested == null ? "" : String.valueOf(nested);
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private boolean valuesEqual(String excel, String system, String valueType, Tolerances tolerances) {
        Tolerances tol = tolerances != null ? tolerances : Tolerances.builder().build();
        if (isBlank(excel) && isBlank(system)) return true;
        if ("money".equalsIgnoreCase(valueType) || "number".equalsIgnoreCase(valueType)) {
            BigDecimal a = parseNumber(excel);
            BigDecimal b = parseNumber(system);
            if (a == null && b == null) return true;
            if (a == null || b == null) return false;
            double t = "money".equalsIgnoreCase(valueType) ? tol.getMoney()
                    : (a.abs().compareTo(BigDecimal.ONE) <= 0 && b.abs().compareTo(BigDecimal.ONE) <= 0
                    ? tol.getFactor() : tol.getDays());
            return a.subtract(b).abs().doubleValue() <= t;
        }
        return nullToEmpty(excel).trim().equalsIgnoreCase(nullToEmpty(system).trim());
    }

    private BigDecimal delta(String excel, String system, String valueType) {
        if (!"money".equalsIgnoreCase(valueType) && !"number".equalsIgnoreCase(valueType)) return null;
        BigDecimal a = parseNumber(excel);
        BigDecimal b = parseNumber(system);
        if (a == null || b == null) return null;
        return a.subtract(b).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal parseNumber(String raw) {
        if (isBlank(raw) || "-".equals(raw.trim()) || ".".equals(raw.trim())) return null;
        String cleaned = raw.replace(",", "")
                .replace("(", "-")
                .replace(")", "")
                .replaceAll("[^0-9.\\-]", "")
                .trim();
        if (isBlank(cleaned) || "-".equals(cleaned) || ".".equals(cleaned)) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    private ReconciliationRun requireRun(String runId) {
        return runRepo.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation run not found: " + runId));
    }

    private ReconciliationMapping requireReadyMapping(String companyId) {
        ReconciliationMapping mapping = getMapping(companyId);
        MappingStatusResponse status = evaluateReadiness(mapping);
        if (!status.isReady()) {
            throw new IllegalArgumentException("Reconciliation mapping is incomplete");
        }
        return mappingRepo.findByCompanyId(companyId).orElse(mapping);
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static String upper(String s) { return s == null ? "" : s.trim().toUpperCase(Locale.ROOT); }
    private static String normalize(String s) { return s == null ? "" : s.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT); }
    private static String coalesce(String... vals) {
        for (String v : vals) if (!isBlank(v)) return v;
        return "";
    }

    private static class SystemSnapshot {
        String employeeId;
        String employeeCode;
        String fullName;
        String legalEntityName;
        String hireDate;
        String exitDate;
        String role;
        PaymentInfo paymentInfo;
    }
}

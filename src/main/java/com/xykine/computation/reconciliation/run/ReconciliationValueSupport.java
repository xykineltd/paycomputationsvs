package com.xykine.computation.reconciliation.run;

import com.xykine.computation.dto.EmployeeDetail;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.reconciliation.mapping.ReconciliationTolerances;
import org.xykine.payroll.model.PaymentInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class ReconciliationValueSupport {

    /** Whole or decimal amounts such as 7000 and 7000.00 — not employee codes like MON0311. */
    private static final Pattern PLAIN_NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");

    private ReconciliationValueSupport() {
    }

    /** Excel header → generateReport key when mapping still uses the sheet title as systemPath. */
    private static final Map<String, String> EXCEL_TO_REPORT_KEY = Map.ofEntries(
            Map.entry("MONTHLY GROSS EARNED", "GROSS PAY"),
            Map.entry("GROSS INCOME", "GROSS PAY"),
            Map.entry("MONTHLY NHF", "NHF"),
            Map.entry("MONTHLY EMPLOYEE PENSION @ 8%", "EMPLOYEE PENSION"),
            Map.entry("MONTHLY VOLUNTARY PENSION", "VOLUNTARY PENSION CONTRIBUTION"),
            Map.entry("PAYE TAX", "PAYE"),
            Map.entry("TOTAL DEDUCTIONS", "TOTAL DEDUCTION"),
            Map.entry("NET SALARY", "NETPAY"),
            Map.entry("ER PENSION", "EMPLOYER PENSION"),
            Map.entry("HIRE DATE", "HIRE DATE"),
            Map.entry("REFERAL BONUS", "REFERRAL BONUS"),
            Map.entry("TOTAL RELIEF", "TOTAL TAX RELIEF")
    );

    /** Excel header → system keys that must be summed before compare. */
    private static final Map<String, List<String>> EXCEL_TO_SUMMED_SYSTEM_KEYS = Map.of(
            "OTHER ALLOWANCE", List.of(
                    "UTILITY",
                    "ENTERTAINMENT",
                    "MEDICAL",
                    "PERSONAL OUTFIT",
                    "LEAVE",
                    "TRAINING"
            )
    );

    static Map<String, Object> flattenSystemRow(ReportResponse report, EmployeeDetail employee) {
        Map<String, Object> out = new LinkedHashMap<>();
        PaymentInfo info = report.getDetail() != null ? report.getDetail().getReport() : null;

        String employeeCode = employee != null && employee.getMappedId() != null && !employee.getMappedId().isBlank()
                ? employee.getMappedId().trim()
                : null;
        if (employeeCode == null && report.getEmployeeCode() != null && !report.getEmployeeCode().isBlank()) {
            employeeCode = report.getEmployeeCode().trim();
        }
        if (employeeCode == null && report.getCode() != null && !report.getCode().isBlank()) {
            employeeCode = report.getCode().trim();
        }

        out.put("employeeCode", employeeCode);
        out.put("employeeId", report.getEmployeeId());
        out.put("fullName", firstNonBlank(
                report.getFullName(),
                info != null ? info.getFullName() : null,
                employee != null ? employee.getName() : null));
        out.put("departmentId", report.getDepartmentId());
        if (info != null) {
            out.put("departmentName", info.getDepartmentName());
            out.put("netPay", info.getNetPay());
            out.put("basicSalary", info.getBasicSalary());
            out.put("unpaidLeaveDays", info.getNumberOfDaysOfUnpaidAbsence());
            putMap(out, "grossPay", info.getGrossPay());
            putMap(out, "deduction", info.getDeduction());
            putMap(out, "taxRelief", info.getTaxRelief());
            putMap(out, "payeeTax", info.getPayeeTax());
            putMap(out, "earning", info.getEarning());
            putMap(out, "nhf", info.getNhf());
            putMap(out, "others", info.getOthers());
            putMap(out, "pension", info.getPension());
        }
        if (employee != null) {
            out.put("employeeHireDate", blankToNull(employee.getHireDate()));
            out.put("exitDate", blankToNull(employee.getExitDate()));
            out.put("role", blankToNull(employee.getRole()));
        } else if (report.getEmployeeHireDate() != null) {
            out.put("employeeHireDate", report.getEmployeeHireDate());
        }
        return out;
    }

    /**
     * Look up a system cell using generateReport keys first (excel header, then systemPath),
     * then dotted PaymentInfo paths as a fallback.
     */
    static Object lookupSystemValue(Map<String, Object> row, String excelHeader, String systemPath) {
        Object composite = sumMappedComponents(row, excelHeader);
        if (!isAbsent(composite)) {
            return toMonthlyIfAnnualRelief(excelHeader, systemPath, composite);
        }
        Object found = lookupKey(row, systemPath);
        if (!isAbsent(found)) {
            return toMonthlyIfAnnualRelief(excelHeader, systemPath, found);
        }
        found = lookupKey(row, excelHeader);
        if (!isAbsent(found)) {
            return toMonthlyIfAnnualRelief(excelHeader, systemPath, found);
        }
        found = lookupKey(row, EXCEL_TO_REPORT_KEY.get(normalizeHeader(excelHeader)));
        if (!isAbsent(found)) {
            return toMonthlyIfAnnualRelief(excelHeader, systemPath, found);
        }
        if (systemPath != null && systemPath.contains(".")) {
            found = getByPath(row, systemPath);
            if (!isAbsent(found)) {
                return toMonthlyIfAnnualRelief(excelHeader, systemPath, found);
            }
            found = lookupKey(row, systemPath.substring(systemPath.lastIndexOf('.') + 1));
        }
        return toMonthlyIfAnnualRelief(excelHeader, systemPath, isAbsent(found) ? null : found);
    }

    /**
     * Excel RENT RELIEF is monthly (annual / 12). System stores the annual amount.
     * Convert when the system value is greater than zero so the compare uses the same basis.
     */
    private static Object toMonthlyIfAnnualRelief(String excelHeader, String systemPath, Object value) {
        if (!isRentReliefField(excelHeader, systemPath)) {
            return value;
        }
        BigDecimal amount = toBigDecimal(value);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return value;
        }
        return amount.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    private static boolean isRentReliefField(String excelHeader, String systemPath) {
        return "RENT RELIEF".equals(normalizeHeader(excelHeader))
                || "RENT RELIEF".equals(normalizeHeader(systemPath));
    }

    private static Object sumMappedComponents(Map<String, Object> row, String excelHeader) {
        List<String> parts = EXCEL_TO_SUMMED_SYSTEM_KEYS.get(normalizeHeader(excelHeader));
        if (row == null || parts == null || parts.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (String part : parts) {
            Double n = lookupNumericComponent(row, part);
            if (n != null) {
                sum = sum.add(BigDecimal.valueOf(n));
                any = true;
            }
        }
        return any ? sum : null;
    }

    private static Double lookupNumericComponent(Map<String, Object> row, String name) {
        Object found = lookupKey(row, name);
        if (!isAbsent(found)) {
            return toNumber(found);
        }
        for (String nest : List.of("earning", "grossPay", "others")) {
            Object nested = row.get(nest);
            if (nested instanceof Map<?, ?> map) {
                Object v = lookupInMap(map, name);
                if (!isAbsent(v)) {
                    return toNumber(v);
                }
            }
        }
        return null;
    }

    static Object getByPath(Map<String, Object> row, String path) {
        if (row == null || path == null || path.isBlank()) {
            return null;
        }
        Object direct = lookupKey(row, path);
        if (!isAbsent(direct)) {
            return direct;
        }
        String[] parts = path.split("\\.");
        Object current = row;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = lookupInMap(map, part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH)
    };

    static boolean valuesEqual(Object excelValue, Object systemValue, String valueType, ReconciliationTolerances tolerances) {
        double moneyTol = tolerances != null && tolerances.getMoney() != null ? tolerances.getMoney() : 0.01;
        double daysTol = tolerances != null && tolerances.getDays() != null ? tolerances.getDays() : 0;
        double factorTol = tolerances != null && tolerances.getFactor() != null ? tolerances.getFactor() : 0.0001;

        // Blank, dash placeholders, and numeric 0 are the same empty value — not a mismatch.
        if (isAbsentOrZero(excelValue) && isAbsentOrZero(systemValue)) {
            return true;
        }
        if ("money".equalsIgnoreCase(valueType) || "number".equalsIgnoreCase(valueType)) {
            Double a = toNumber(excelValue);
            Double b = toNumber(systemValue);
            if (a == null && b == null) {
                return true;
            }
            if (a == null || b == null) {
                return false;
            }
            double tol = "money".equalsIgnoreCase(valueType)
                    ? moneyTol
                    : (Math.abs(a) <= 1 && Math.abs(b) <= 1 ? factorTol : daysTol);
            return Math.abs(a - b) <= tol;
        }

        LocalDate excelDate = toDate(excelValue);
        LocalDate systemDate = toDate(systemValue);
        if (excelDate != null && systemDate != null) {
            return excelDate.equals(systemDate);
        }
        if ("date".equalsIgnoreCase(valueType)) {
            return false;
        }

        // Text fields that are both plain amounts (7000 vs 7000.00) compare numerically.
        if (looksLikePlainNumber(excelValue) && looksLikePlainNumber(systemValue)) {
            BigDecimal a = toBigDecimal(excelValue);
            BigDecimal b = toBigDecimal(systemValue);
            if (a == null && b == null) {
                return true;
            }
            if (a == null || b == null) {
                return false;
            }
            return a.compareTo(b) == 0;
        }

        String a = String.valueOf(excelValue == null ? "" : excelValue).trim().toLowerCase(Locale.ROOT);
        String b = String.valueOf(systemValue == null ? "" : systemValue).trim().toLowerCase(Locale.ROOT);
        return a.equals(b);
    }

    static Object delta(Object excelValue, Object systemValue, String valueType) {
        if (!isNumericValueType(valueType)
                && !(looksLikePlainNumber(excelValue) && looksLikePlainNumber(systemValue))) {
            return null;
        }
        Double a = toNumber(excelValue);
        Double b = toNumber(systemValue);
        if (a == null || b == null) {
            return null;
        }
        return BigDecimal.valueOf(a - b);
    }

    static Double toNumber(Object value) {
        if (isAbsent(value)) {
            return null;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        String cleaned = String.valueOf(value)
                .replace(",", "")
                .replace("(", "-")
                .replace(")", "")
                .replaceAll("[^0-9.\\-]", "")
                .trim();
        if (cleaned.isBlank() || "-".equals(cleaned) || ".".equals(cleaned)) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static BigDecimal toBigDecimal(Object value) {
        if (isAbsent(value)) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        String cleaned = String.valueOf(value).trim().replace(",", "");
        if (cleaned.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            Double parsed = toNumber(value);
            return parsed == null ? null : BigDecimal.valueOf(parsed);
        }
    }

    static boolean looksLikePlainNumber(Object value) {
        if (isAbsent(value)) {
            return false;
        }
        if (value instanceof Number) {
            return true;
        }
        String s = String.valueOf(value).trim().replace(",", "");
        return PLAIN_NUMBER.matcher(s).matches();
    }

    private static boolean isNumericValueType(String valueType) {
        return "money".equalsIgnoreCase(valueType) || "number".equalsIgnoreCase(valueType);
    }

    static LocalDate toDate(Object value) {
        if (isAbsent(value)) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        String raw = String.valueOf(value).trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, formatter);
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        return null;
    }

    static String normalizeEmpId(String raw) {
        if (raw == null) {
            return "";
        }
        String v = raw.trim().toUpperCase(Locale.ROOT);
        if (v.endsWith(".0") && v.matches("\\d+\\.0")) {
            v = v.substring(0, v.length() - 2);
        }
        return v;
    }

    private static Object lookupKey(Map<String, Object> row, String key) {
        if (row == null || key == null || key.isBlank()) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return lookupInMap(row, key);
    }

    private static Object lookupInMap(Map<?, ?> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object exact = map.get(key);
        if (!isAbsent(exact)) {
            return exact;
        }
        String header = normalizeHeader(key);
        String compact = compactHeader(key);
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String candidate = String.valueOf(e.getKey());
            if (normalizeHeader(candidate).equals(header) || compactHeader(candidate).equals(compact)) {
                if (!isAbsent(e.getValue())) {
                    return e.getValue();
                }
            }
        }
        return isAbsent(exact) ? null : exact;
    }

    private static String normalizeHeader(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }

    private static String compactHeader(String value) {
        return normalizeHeader(value).replaceAll("[^A-Z0-9]", "");
    }

    private static void putMap(Map<String, Object> out, String key, Map<String, BigDecimal> map) {
        if (map != null && !map.isEmpty()) {
            out.put(key, new LinkedHashMap<>(map));
        }
    }

    /** Empty, dash placeholders, and numeric zero all mean "no value". */
    static boolean isAbsentOrZero(Object value) {
        if (isAbsent(value)) {
            return true;
        }
        Double n = toNumber(value);
        return n != null && n == 0.0;
    }

    static boolean isAbsent(Object value) {
        if (value == null) {
            return true;
        }
        String t = String.valueOf(value).trim();
        if (t.isEmpty()) {
            return true;
        }
        return "-".equals(t) || "—".equals(t) || "–".equals(t) || ".".equals(t)
                || "n/a".equalsIgnoreCase(t) || "na".equalsIgnoreCase(t)
                || "nil".equalsIgnoreCase(t) || "none".equalsIgnoreCase(t);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}

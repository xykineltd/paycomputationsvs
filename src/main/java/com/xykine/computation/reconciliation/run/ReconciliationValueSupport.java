package com.xykine.computation.reconciliation.run;

import com.xykine.computation.dto.EmployeeDetail;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.reconciliation.mapping.ReconciliationTolerances;
import org.xykine.payroll.model.PaymentInfo;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class ReconciliationValueSupport {

    private ReconciliationValueSupport() {
    }

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

    static Object getByPath(Map<String, Object> row, String path) {
        if (row == null || path == null || path.isBlank()) {
            return null;
        }
        if (row.containsKey(path)) {
            return row.get(path);
        }
        String[] parts = path.split("\\.");
        Object current = row;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
            if (current == null) {
                // try case-insensitive map key
                current = map.entrySet().stream()
                        .filter(e -> String.valueOf(e.getKey()).equalsIgnoreCase(part))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null);
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    static boolean valuesEqual(Object excelValue, Object systemValue, String valueType, ReconciliationTolerances tolerances) {
        double moneyTol = tolerances != null && tolerances.getMoney() != null ? tolerances.getMoney() : 0.01;
        double daysTol = tolerances != null && tolerances.getDays() != null ? tolerances.getDays() : 0;
        double factorTol = tolerances != null && tolerances.getFactor() != null ? tolerances.getFactor() : 0.0001;

        if (isEmpty(excelValue) && isEmpty(systemValue)) {
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

        String a = String.valueOf(excelValue == null ? "" : excelValue).trim().toLowerCase(Locale.ROOT);
        String b = String.valueOf(systemValue == null ? "" : systemValue).trim().toLowerCase(Locale.ROOT);
        return a.equals(b);
    }

    static Object delta(Object excelValue, Object systemValue, String valueType) {
        if (!"money".equalsIgnoreCase(valueType) && !"number".equalsIgnoreCase(valueType)) {
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
        if (value == null || "".equals(value)) {
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

    private static void putMap(Map<String, Object> out, String key, Map<String, BigDecimal> map) {
        if (map != null && !map.isEmpty()) {
            out.put(key, new LinkedHashMap<>(map));
        }
    }

    private static boolean isEmpty(Object value) {
        return value == null || String.valueOf(value).isBlank();
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

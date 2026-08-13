package com.xykine.computation.reconciliation.mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Evaluates whether a reconciliation mapping profile is complete enough to run.
 * Mirrors frontend mapping-utils readiness rules.
 */
public final class ReconciliationMappingReadiness {

    private ReconciliationMappingReadiness() {
    }

    public static ReadinessResult evaluate(ReconciliationMapping mapping) {
        List<String> missing = new ArrayList<>();
        if (mapping == null) {
            missing.add("Mapping profile is missing");
            return new ReadinessResult(false, "INCOMPLETE", missing);
        }

        if (mapping.getHeaderRowIndex() == null || mapping.getHeaderRowIndex() < 1) {
            missing.add("Header row index");
        }
        if (mapping.getDataStartRow() == null || mapping.getDataStartRow() < 1) {
            missing.add("Data start row");
        }
        if (isBlank(mapping.getExcelMatchKey())) {
            missing.add("Excel employee match key");
        }
        if (isBlank(mapping.getSystemMatchKey())) {
            missing.add("System employee match key");
        }

        List<ReconciliationEntityAlias> aliases =
                mapping.getEntityAliases() != null ? mapping.getEntityAliases() : List.of();
        boolean hasCompanySheetAlias = aliases.stream()
                .anyMatch(a -> !isBlank(a.getExcelSheetName()));
        if (!hasCompanySheetAlias) {
            missing.add("Company Excel sheet alias (excelSheetName required)");
        }

        List<ReconciliationColumnMapping> columns =
                mapping.getColumnMappings() != null ? mapping.getColumnMappings() : List.of();

        boolean matchMapped = columns.stream().anyMatch(c -> {
            if (!Boolean.TRUE.equals(c.getEnabled()) || isBlank(c.getSystemPath())) {
                return false;
            }
            if (Boolean.TRUE.equals(c.getIsMatchKey())) {
                return true;
            }
            return normalizeHeader(c.getExcelHeader()).equals(normalizeHeader(mapping.getExcelMatchKey()));
        });
        if (!matchMapped) {
            missing.add("Match key column mapping");
        }

        long hardInputs = columns.stream()
                .filter(c -> Boolean.TRUE.equals(c.getEnabled())
                        && "input".equalsIgnoreCase(c.getStage())
                        && "hard".equalsIgnoreCase(c.getSeverity()))
                .count();
        long hardOutcomes = columns.stream()
                .filter(c -> Boolean.TRUE.equals(c.getEnabled())
                        && "outcome".equalsIgnoreCase(c.getStage())
                        && "hard".equalsIgnoreCase(c.getSeverity()))
                .count();
        if (hardInputs == 0) {
            missing.add("At least one hard input column mapping");
        }
        if (hardOutcomes == 0) {
            missing.add("At least one hard outcome column mapping");
        }

        boolean ready = missing.isEmpty();
        return new ReadinessResult(ready, ready ? "READY" : "INCOMPLETE", missing);
    }

    public static void apply(ReconciliationMapping mapping) {
        ReadinessResult result = evaluate(mapping);
        mapping.setReady(result.ready());
        mapping.setStatus(result.status());
        mapping.setMissing(result.missing());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }

    public record ReadinessResult(boolean ready, String status, List<String> missing) {
    }
}

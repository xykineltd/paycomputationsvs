package com.xykine.computation.reconciliation.run;

import com.xykine.computation.reconciliation.mapping.ReconciliationColumnMapping;
import com.xykine.computation.reconciliation.mapping.ReconciliationEntityAlias;
import com.xykine.computation.reconciliation.mapping.ReconciliationMapping;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class ReconciliationExcelParser {

    public ParsedSheet parse(byte[] bytes, ReconciliationMapping mapping, String companyId, String legalEntityId) {
        ReconciliationEntityAlias alias = resolveAlias(mapping, companyId, legalEntityId);
        if (alias == null || isBlank(alias.getExcelSheetName())) {
            throw new IllegalArgumentException(
                    "No Excel sheet alias configured for companyId=" + companyId
                            + ". Save Reconciliation Mapping with a company sheet alias "
                            + "(entityAliases[].excelSheetName) in collection reconciliationMappings.");
        }

        int headerRowIndex1Based = mapping.getHeaderRowIndex() != null ? mapping.getHeaderRowIndex() : 1;
        int dataStartRow1Based = mapping.getDataStartRow() != null ? mapping.getDataStartRow() : headerRowIndex1Based + 1;
        String matchKeyHeader = mapping.getExcelMatchKey() != null ? mapping.getExcelMatchKey() : "EMP ID";

        try (var in = new ByteArrayInputStream(bytes); Workbook wb = new XSSFWorkbook(in)) {
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = findSheet(wb, alias.getExcelSheetName());
            if (sheet == null) {
                throw new IllegalArgumentException(
                        "Excel sheet not found: " + alias.getExcelSheetName());
            }

            int headerIdx = headerRowIndex1Based - 1;
            Row headerRow = sheet.getRow(headerIdx);
            if (headerRow == null) {
                throw new IllegalArgumentException("Header row not found at row " + headerRowIndex1Based);
            }

            Map<String, Integer> colByNormalized = new LinkedHashMap<>();
            Map<Integer, String> headerByIndex = new LinkedHashMap<>();
            for (Cell cell : headerRow) {
                String header = cellValueAsString(cell, formatter, evaluator);
                if (isBlank(header)) {
                    continue;
                }
                colByNormalized.put(normalizeHeader(header), cell.getColumnIndex());
                headerByIndex.put(cell.getColumnIndex(), header.trim());
            }

            Integer matchCol = colByNormalized.get(normalizeHeader(matchKeyHeader));
            if (matchCol == null) {
                throw new IllegalArgumentException("Match key column not found in Excel: " + matchKeyHeader);
            }

            List<PayrollReconciliationTempRow> rows = new ArrayList<>();
            int dataStartIdx = dataStartRow1Based - 1;
            for (int r = dataStartIdx; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String matchValue = cellValueAsString(row.getCell(matchCol), formatter, evaluator);
                if (isBlank(matchValue)) {
                    continue;
                }

                Map<String, Object> cells = new LinkedHashMap<>();
                for (Map.Entry<Integer, String> entry : headerByIndex.entrySet()) {
                    Object value = cellValue(row.getCell(entry.getKey()));
                    cells.put(entry.getValue(), value);
                }

                rows.add(PayrollReconciliationTempRow.builder()
                        .rowNumber(r + 1)
                        .matchKeyValue(matchValue.trim())
                        .cells(cells)
                        .build());
            }

            if (rows.isEmpty()) {
                throw new IllegalArgumentException(
                        "No employee rows found on sheet \"" + alias.getExcelSheetName()
                                + "\" (header row " + headerRowIndex1Based
                                + ", data from row " + dataStartRow1Based
                                + ", match column \"" + matchKeyHeader + "\"). "
                                + "Check the sheet name, header/data rows, and that EMP ID cells are populated.");
            }

            return new ParsedSheet(alias.getExcelSheetName(), rows, matchKeyHeader);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse reconciliation Excel file", e);
        }
    }

    /**
     * Resolve sheet alias from the company mapping document ({@code reconciliationMappings}).
     * Order: companyId match → legalEntityId match (optional) → first alias with a sheet name.
     */
    private ReconciliationEntityAlias resolveAlias(
            ReconciliationMapping mapping,
            String companyId,
            String legalEntityId
    ) {
        if (mapping.getEntityAliases() == null || mapping.getEntityAliases().isEmpty()) {
            return null;
        }

        List<ReconciliationEntityAlias> aliases = mapping.getEntityAliases();

        Optional<ReconciliationEntityAlias> byCompany = aliases.stream()
                .filter(a -> companyId != null && companyId.equals(String.valueOf(a.getCompanyId())))
                .filter(a -> !isBlank(a.getExcelSheetName()))
                .findFirst();
        if (byCompany.isPresent()) {
            return byCompany.get();
        }

        if (!isBlank(legalEntityId)) {
            Optional<ReconciliationEntityAlias> byEntity = aliases.stream()
                    .filter(a -> legalEntityId.equals(String.valueOf(a.getLegalEntityId())))
                    .filter(a -> !isBlank(a.getExcelSheetName()))
                    .findFirst();
            if (byEntity.isPresent()) {
                return byEntity.get();
            }
        }

        return aliases.stream()
                .filter(a -> !isBlank(a.getExcelSheetName()))
                .findFirst()
                .orElse(null);
    }

    private Sheet findSheet(Workbook wb, String sheetName) {
        Sheet exact = wb.getSheet(sheetName);
        if (exact != null) {
            return exact;
        }
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            Sheet s = wb.getSheetAt(i);
            if (s != null && normalizeHeader(s.getSheetName()).equals(normalizeHeader(sheetName))) {
                return s;
            }
        }
        return null;
    }

    static String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }

    private static Object cellValue(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        return switch (cell.getCellType()) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue() != null
                            ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                            : null;
                }
                double n = cell.getNumericCellValue();
                if (n == Math.rint(n) && Math.abs(n) < 1_000_000_000_000d) {
                    yield BigDecimal.valueOf((long) n);
                }
                yield BigDecimal.valueOf(n);
            }
            case BOOLEAN -> cell.getBooleanCellValue();
            case FORMULA -> {
                try {
                    yield BigDecimal.valueOf(cell.getNumericCellValue());
                } catch (Exception ignored) {
                    yield cell.toString() != null ? cell.toString().trim() : null;
                }
            }
            default -> {
                String s = cell.toString();
                yield s != null ? s.trim() : null;
            }
        };
    }

    private static String cellValueAsString(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }
        String v = formatter.formatCellValue(cell, evaluator);
        if (v == null) {
            return null;
        }
        v = v.replaceAll("\\s+", " ").trim();
        return v.isEmpty() ? null : v;
    }

    private static String cellValueAsString(Cell cell) {
        Object v = cellValue(cell);
        return v == null ? null : String.valueOf(v);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public record ParsedSheet(String sheetName, List<PayrollReconciliationTempRow> rows, String matchKeyHeader) {
    }

    /** Resolve excel cell for a mapped column, matching headers case-insensitively. */
    public static Object cellForHeader(Map<String, Object> cells, String excelHeader) {
        if (cells == null || excelHeader == null) {
            return null;
        }
        if (cells.containsKey(excelHeader)) {
            return cells.get(excelHeader);
        }
        String target = normalizeHeader(excelHeader);
        for (Map.Entry<String, Object> e : cells.entrySet()) {
            if (normalizeHeader(e.getKey()).equals(target)) {
                return e.getValue();
            }
        }
        return null;
    }

    public static List<ReconciliationColumnMapping> enabledColumns(ReconciliationMapping mapping, String stage) {
        if (mapping.getColumnMappings() == null) {
            return List.of();
        }
        return mapping.getColumnMappings().stream()
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .filter(c -> stage.equalsIgnoreCase(c.getStage()))
                .toList();
    }
}

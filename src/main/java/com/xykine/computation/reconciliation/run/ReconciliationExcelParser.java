package com.xykine.computation.reconciliation.run;

import com.xykine.computation.reconciliation.mapping.ReconciliationColumnMapping;
import com.xykine.computation.reconciliation.mapping.ReconciliationEntityAlias;
import com.xykine.computation.reconciliation.mapping.ReconciliationMapping;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

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

        try (Workbook wb = openWorkbookWithoutExternalLinks(bytes)) {
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = findSheet(wb, alias.getExcelSheetName());
            if (sheet == null) {
                throw new IllegalArgumentException(
                        "Excel sheet not found: " + alias.getExcelSheetName()
                                + ". Available sheets: " + availableSheetNames(wb)
                                + ". Update the company Excel sheet alias (entityAliases[].excelSheetName)"
                                + " to match a tab name in the uploaded file.");
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

    /**
     * Excel files often cache unused external-workbook links. POI parses that XML with JAXP,
     * which on recent JDKs rejects entities over 100,000 chars. Reconciliation only needs
     * cell values, so drop those zip parts (and their relationships) before opening.
     */
    private Workbook openWorkbookWithoutExternalLinks(byte[] bytes) throws IOException {
        byte[] sanitized = stripExternalLinkZipEntries(bytes);
        return new XSSFWorkbook(new ByteArrayInputStream(sanitized));
    }

    private static byte[] stripExternalLinkZipEntries(byte[] bytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(32, bytes.length));
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes));
             ZipOutputStream out = new ZipOutputStream(baos)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.contains("/externalLinks/") || name.startsWith("xl/externalLinks/")) {
                    continue;
                }
                byte[] data = in.readAllBytes();
                if (name.endsWith(".rels") || "[Content_Types].xml".equals(name)) {
                    String xml = new String(data, StandardCharsets.UTF_8);
                    xml = xml.replaceAll("(?is)<Relationship\\b[^>]*externalLink[^>]*/>", "");
                    xml = xml.replaceAll("(?is)<Override\\b[^>]*externalLink[^>]*/>", "");
                    data = xml.getBytes(StandardCharsets.UTF_8);
                }
                ZipEntry copy = new ZipEntry(name);
                out.putNextEntry(copy);
                out.write(data);
                out.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private Sheet findSheet(Workbook wb, String sheetName) {
        if (isBlank(sheetName)) {
            return null;
        }
        Sheet exact = wb.getSheet(sheetName);
        if (exact != null) {
            return exact;
        }

        List<Sheet> visible = visibleSheets(wb);
        String wanted = normalizeSheetName(sheetName);
        String wantedKey = sheetKey(sheetName);

        for (Sheet s : visible) {
            if (normalizeSheetName(s.getSheetName()).equals(wanted)) {
                return s;
            }
        }
        for (Sheet s : visible) {
            if (sheetKey(s.getSheetName()).equals(wantedKey)) {
                return s;
            }
        }

        List<Sheet> partial = new ArrayList<>();
        for (Sheet s : visible) {
            String actual = normalizeSheetName(s.getSheetName());
            String actualKey = sheetKey(s.getSheetName());
            if (actual.contains(wanted) || wanted.contains(actual)
                    || actualKey.contains(wantedKey) || wantedKey.contains(actualKey)) {
                partial.add(s);
            }
        }
        if (partial.size() == 1) {
            return partial.get(0);
        }
        if (visible.size() == 1) {
            return visible.get(0);
        }
        return null;
    }

    private List<Sheet> visibleSheets(Workbook wb) {
        List<Sheet> sheets = new ArrayList<>();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            if (wb.isSheetHidden(i) || wb.isSheetVeryHidden(i)) {
                continue;
            }
            Sheet s = wb.getSheetAt(i);
            if (s != null) {
                sheets.add(s);
            }
        }
        return sheets;
    }

    private String availableSheetNames(Workbook wb) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            Sheet s = wb.getSheetAt(i);
            if (s != null) {
                names.add(s.getSheetName());
            }
        }
        return names.isEmpty() ? "(none)" : String.join(", ", names);
    }

    private static String normalizeSheetName(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ')
                .replaceAll("[\\p{Cf}]", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private static String sheetKey(String value) {
        return normalizeSheetName(value).replaceAll("[^A-Z0-9]", "");
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

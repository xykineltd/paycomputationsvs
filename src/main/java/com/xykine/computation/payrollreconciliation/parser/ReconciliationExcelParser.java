package com.xykine.computation.payrollreconciliation.parser;

import com.xykine.computation.payrollreconciliation.dto.EntityAlias;
import com.xykine.computation.payrollreconciliation.entity.ReconciliationExcelRow;
import com.xykine.computation.payrollreconciliation.entity.ReconciliationMapping;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.*;

@Component
public class ReconciliationExcelParser {

    public record ParseResult(String sheetName, List<ReconciliationExcelRow> rows) {}

    public ParseResult parse(byte[] bytes, ReconciliationMapping mapping, EntityAlias alias) {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            String sheetName = resolveSheet(wb, alias);
            if (sheetName == null) {
                throw new IllegalArgumentException(
                        "No Excel sheet mapped for " + (alias.getLegalEntityName() != null
                                ? alias.getLegalEntityName() : "selected entity"));
            }
            Sheet sheet = wb.getSheet(sheetName);
            int headerIdx = Math.max(mapping.getHeaderRowIndex() - 1, 0);
            int dataStart = Math.max(mapping.getDataStartRow() - 1, headerIdx + 1);
            Row headerRow = sheet.getRow(headerIdx);
            if (headerRow == null) {
                throw new IllegalArgumentException("Header row " + mapping.getHeaderRowIndex() + " not found on sheet " + sheetName);
            }

            List<String> headers = new ArrayList<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                headers.add(cellString(headerRow.getCell(c)).replaceAll("\\s+", " ").trim());
            }

            String matchKey = mapping.getExcelMatchKey() != null ? mapping.getExcelMatchKey() : "EMP ID";
            int matchCol = indexOfHeader(headers, matchKey);
            if (matchCol < 0) {
                throw new IllegalArgumentException("Excel header \"" + matchKey + "\" not found on sheet \"" + sheetName + "\"");
            }
            int nameCol = indexOfHeader(headers, "EMPLOYEE NAME");
            int leCol = indexOfHeader(headers, "LEGAL ENTITY");

            List<ReconciliationExcelRow> rows = new ArrayList<>();
            for (int r = dataStart; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String empId = cellString(row.getCell(matchCol)).trim();
                if (empId.isEmpty()) continue;

                if (leCol >= 0 && alias.getExcelLegalEntityValue() != null && !alias.getExcelLegalEntityValue().isBlank()) {
                    String leVal = cellString(row.getCell(leCol)).trim();
                    if (!leVal.isEmpty() && !leVal.equalsIgnoreCase(alias.getExcelLegalEntityValue().trim())) {
                        continue;
                    }
                }

                Map<String, String> values = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    String h = headers.get(c);
                    if (h == null || h.isBlank()) continue;
                    values.put(h, cellString(row.getCell(c)).trim());
                }

                rows.add(ReconciliationExcelRow.builder()
                        .employeeCode(empId.toUpperCase(Locale.ROOT))
                        .employeeName(nameCol >= 0 ? cellString(row.getCell(nameCol)).trim() : "")
                        .rowNumber(r + 1)
                        .values(values)
                        .build());
            }
            return new ParseResult(sheetName, rows);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse Excel file: " + e.getMessage(), e);
        }
    }

    private String resolveSheet(Workbook wb, EntityAlias alias) {
        if (alias.getExcelSheetName() != null && !alias.getExcelSheetName().isBlank()) {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                String name = wb.getSheetName(i);
                if (name.equalsIgnoreCase(alias.getExcelSheetName().trim())) return name;
            }
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                String name = wb.getSheetName(i);
                if (name.toLowerCase(Locale.ROOT).contains(alias.getExcelSheetName().trim().toLowerCase(Locale.ROOT))) {
                    return name;
                }
            }
        }
        if (alias.getExcelLegalEntityValue() != null && !alias.getExcelLegalEntityValue().isBlank()) {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                Row header = sheet.getRow(5); // default header row index 6
                if (header == null) continue;
                int leCol = -1;
                for (int c = 0; c < header.getLastCellNum(); c++) {
                    if ("LEGAL ENTITY".equalsIgnoreCase(cellString(header.getCell(c)).replaceAll("\\s+", " ").trim())) {
                        leCol = c;
                        break;
                    }
                }
                if (leCol < 0) continue;
                for (int r = 6; r <= Math.min(sheet.getLastRowNum(), 30); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    String v = cellString(row.getCell(leCol)).trim();
                    if (v.equalsIgnoreCase(alias.getExcelLegalEntityValue().trim())) {
                        return wb.getSheetName(i);
                    }
                }
            }
        }
        return null;
    }

    private int indexOfHeader(List<String> headers, String target) {
        String t = normalize(target);
        for (int i = 0; i < headers.size(); i++) {
            if (normalize(headers.get(i)).equals(t)) return i;
        }
        return -1;
    }

    private String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }

    private String cellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double n = cell.getNumericCellValue();
                if (n == Math.rint(n)) yield String.valueOf((long) n);
                yield String.valueOf(n);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    try {
                        yield cell.getStringCellValue();
                    } catch (Exception ex) {
                        yield "";
                    }
                }
            }
            case BLANK, _NONE, ERROR -> "";
        };
    }
}

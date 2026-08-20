package com.xykine.computation.service;

import com.xykine.computation.dto.GLSummary;
import com.xykine.computation.entity.CompanyMetadata;
import com.xykine.computation.entity.PayrollGLReport;
import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.exceptions.PayrollReportNotException;
import com.xykine.computation.repo.PayrollGLReportRepository;
import com.xykine.computation.repo.PayrollReportSummaryRepo;
import com.xykine.computation.response.PayrollGLLineResponse;
import com.xykine.computation.response.PayrollGLReportResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayrollGLReportQueryService {

    private static final String[] NETSUITE_HEADERS = {
            "Netsuite ID",
            "DR",
            "CR",
            "Function",
            "Department",
            "Line Memo",
            "Header Memo",
            "Date (Req)",
            "Posting Period",
            "currency",
            "Name",
            "Subsidiary"
    };

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter NETSUITE_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter POSTING_PERIOD = DateTimeFormatter.ofPattern("MMM-yy", Locale.ENGLISH);
    private static final DateTimeFormatter HEADER_MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final PayrollGLReportRepository payrollGLReportRepository;
    private final PayrollReportSummaryRepo payrollReportSummaryRepo;
    private final CompanyMetadataService companyMetadataService;

    public PayrollGLReportResponse getReport(String companyId, String reportId) {
        LoadedReport loaded = load(companyId, reportId);
        return toResponse(loaded);
    }

    public byte[] downloadNetsuiteExcel(String companyId, String reportId) throws IOException {
        LoadedReport loaded = load(companyId, reportId);
        PayrollGLReportResponse response = toResponse(loaded);
        String companyName = companyName(loaded.companyId());
        String headerMemo = headerMemo(loaded.summary());
        String postingDate = netsuiteDate(loaded.summary());
        String postingPeriod = postingPeriod(loaded.summary());

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Netsuite");
            Row header = sheet.createRow(0);
            for (int i = 0; i < NETSUITE_HEADERS.length; i++) {
                header.createCell(i).setCellValue(NETSUITE_HEADERS[i]);
            }

            int rowIndex = 1;
            for (PayrollGLLineResponse line : response.getLines()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(nullToEmpty(line.getGlCode()));
                row.createCell(1).setCellValue(decimal(line.getDebit()).doubleValue());
                row.createCell(2).setCellValue(decimal(line.getCredit()).doubleValue());
                row.createCell(3).setCellValue(companyName);
                row.createCell(4).setCellValue(companyName);
                row.createCell(5).setCellValue("");
                row.createCell(6).setCellValue(headerMemo);
                row.createCell(7).setCellValue(postingDate);
                row.createCell(8).setCellValue(postingPeriod);
                row.createCell(9).setCellValue("NGN");
                row.createCell(10).setCellValue("Not Applicable (" + "NGN" + ")");
                row.createCell(11).setCellValue(companyName);
            }

            for (int i = 0; i < NETSUITE_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] downloadNetsuiteCsv(String companyId, String reportId) {
        LoadedReport loaded = load(companyId, reportId);
        PayrollGLReportResponse response = toResponse(loaded);
        String companyName = companyName(loaded.companyId());
        String headerMemo = headerMemo(loaded.summary());
        String postingDate = netsuiteDate(loaded.summary());
        String postingPeriod = postingPeriod(loaded.summary());

        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", NETSUITE_HEADERS)).append('\n');
        for (PayrollGLLineResponse line : response.getLines()) {
            csv.append(csvCell(line.getGlCode())).append(',')
                    .append(csvNumber(line.getDebit())).append(',')
                    .append(csvNumber(line.getCredit())).append(',')
                    .append(csvCell(companyName)).append(',')
                    .append(csvCell(companyName)).append(',')
                    .append(csvCell("")).append(',')
                    .append(csvCell(headerMemo)).append(',')
                    .append(csvCell(postingDate)).append(',')
                    .append(csvCell(postingPeriod)).append(',')
                    .append(csvCell("NGN")).append(',')
                    .append(csvCell("Not Applicable (NGN)")).append(',')
                    .append(csvCell(companyName))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public String downloadFileName(String companyId, String reportId, String extension) {
        LoadedReport loaded = load(companyId, reportId);
        String period = postingPeriod(loaded.summary()).replace(" ", "");
        if (period.isBlank()) {
            return "GL-Netsuite." + extension;
        }
        return "GL-Netsuite-" + period + "." + extension;
    }

    /**
     * payroll_gl_reports is keyed by _id (payroll reportId). It does not store companyId.
     * companyId is only used for NetSuite labels and optional payroll_summary dates.
     */
    private LoadedReport load(String companyId, String reportId) {
        if (reportId == null || reportId.isBlank()) {
            throw new PayrollReportNotException("reportId is required");
        }

        String trimmedReportId = reportId.trim();
        PayrollGLReport glReport = payrollGLReportRepository.findById(trimmedReportId)
                .or(() -> payrollGLReportRepository.findFirstByPayrollIdOrderByGeneratedDesc(trimmedReportId))
                .orElseThrow(() -> new PayrollReportNotException(trimmedReportId));

        PayrollReportSummary summary = findSummary(trimmedReportId);
        String resolvedCompanyId = companyId;
        if ((resolvedCompanyId == null || resolvedCompanyId.isBlank()) && summary != null) {
            resolvedCompanyId = summary.getCompanyId();
        }

        return new LoadedReport(summary, glReport, resolvedCompanyId);
    }

    private PayrollReportSummary findSummary(String reportId) {
        try {
            return payrollReportSummaryRepo.findPayrollReportSummaryById(UUID.fromString(reportId));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private PayrollGLReportResponse toResponse(LoadedReport loaded) {
        List<PayrollGLLineResponse> lines = new ArrayList<>();
        Map<String, GLSummary> gls = loaded.glReport().getGls();
        if (gls != null) {
            gls.entrySet().stream()
                    .filter(entry -> entry.getValue() != null)
                    .map(entry -> toLine(entry.getKey(), entry.getValue()))
                    .filter(line -> line.getGlCode() != null && !line.getGlCode().isBlank())
                    .sorted(Comparator.comparing(PayrollGLLineResponse::getGlCode, String.CASE_INSENSITIVE_ORDER))
                    .forEach(lines::add);
        }

        BigDecimal totalDebit = lines.stream().map(PayrollGLLineResponse::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = lines.stream().map(PayrollGLLineResponse::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);

        PayrollReportSummary summary = loaded.summary();
        String reportId = loaded.glReport().getId();
        if (summary != null && summary.getId() != null) {
            reportId = summary.getId().toString();
        }

        return PayrollGLReportResponse.builder()
                .reportId(reportId)
                .companyId(loaded.companyId())
                .startDate(summary == null ? null : summary.getStartDate())
                .endDate(summary == null ? null : summary.getEndDate())
                .generated(loaded.glReport().getGenerated())
                .status(loaded.glReport().getStatus())
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .lines(lines)
                .build();
    }

    private static PayrollGLLineResponse toLine(String mapKey, GLSummary item) {
        String glCode = firstNonBlank(item.getGlCode(), mapKey);
        BigDecimal debit = decimal(item.getDebit());
        BigDecimal credit = decimal(item.getCredit());
        BigDecimal net = item.getNet() == null ? debit.subtract(credit) : decimal(item.getNet());
        return PayrollGLLineResponse.builder()
                .glCode(glCode)
                .glDescription(null)
                .debit(debit)
                .credit(credit)
                .net(net)
                .build();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String companyName(String companyId) {
        if (companyId == null || companyId.isBlank()) {
            return "Company";
        }
        return companyMetadataService.getByCompanyId(companyId)
                .map(CompanyMetadata::getCompanyName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("Company");
    }

    private static String headerMemo(PayrollReportSummary summary) {
        if (summary == null) {
            return "Payroll";
        }
        LocalDate period = parseDate(summary.getEndDate());
        if (period == null) {
            return "Payroll";
        }
        return HEADER_MONTH.format(period) + " Payroll";
    }

    private static String netsuiteDate(PayrollReportSummary summary) {
        if (summary == null) {
            return "";
        }
        LocalDate period = parseDate(summary.getEndDate());
        return period == null ? "" : NETSUITE_DATE.format(period);
    }

    private static String postingPeriod(PayrollReportSummary summary) {
        if (summary == null) {
            return "";
        }
        LocalDate period = parseDate(summary.getEndDate());
        return period == null ? "" : POSTING_PERIOD.format(period);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), ISO_DATE);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(value.trim());
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }

    private static BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        String raw = value.toString().trim();
        if (raw.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(raw);
    }

    private static String csvNumber(BigDecimal value) {
        return decimal(value).stripTrailingZeros().toPlainString();
    }

    private static String csvCell(String value) {
        String raw = nullToEmpty(value);
        if (raw.contains(",") || raw.contains("\"") || raw.contains("\n")) {
            return "\"" + raw.replace("\"", "\"\"") + "\"";
        }
        return raw;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record LoadedReport(PayrollReportSummary summary, PayrollGLReport glReport, String companyId) {
    }
}

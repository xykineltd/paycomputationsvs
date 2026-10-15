package com.xykine.computation.service;


import com.xykine.computation.dto.GLReportStatus;
import com.xykine.computation.dto.GLSummary;
import com.xykine.computation.entity.*;

import com.xykine.computation.repo.*;

import com.xykine.computation.request.EmployeeFilterRequest;
import com.xykine.computation.request.RepaymentRequest;
import com.xykine.computation.response.PayCompteVarianceDetailsCustomized;
import com.xykine.computation.response.PayComputeDetailResponse;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.utils.AuthUtil;
import com.xykine.computation.utils.PayrollMetrics;
import com.xykine.computation.utils.ReportUtils;
import lombok.RequiredArgsConstructor;

import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import org.xykine.payroll.model.MapKeys;
import org.xykine.payroll.model.PaymentInfo;
import org.xykine.payroll.model.enums.PaymentTypeEnum;


import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.xykine.computation.service.ReportPersistenceServiceImpl.mergeMaps;

@Service
@RequiredArgsConstructor
public class PayrollAsyncService {

    private static final int DETAIL_BULK_BATCH_SIZE = 250;

    private final PayrollReportDetailRepo payrollReportDetailRepo;
    private final MongoTemplate mongoTemplate;
    private final LoanService loanService;
    private final YTDReportRepo ytdReportRepo;
    private final PayrollVarianceDetailsCustomizedRepo payrollVarianceDetailsCustomizedRepo;
    private final PaymentElementGLMappingRepository paymentElementGLMappingCustomRepository;
    private final PayrollGLReportRepository payrollGLReportRepository;
    private final AdminService adminService;

    private static final Logger LOGGER = LoggerFactory.getLogger(PayrollAsyncService.class);

    @Async
    public void updateDetailStatusAsync(String summaryId) {
        List<PayrollReportDetail> details = payrollReportDetailRepo.findPayrollReportDetailBySummaryId(summaryId);
        details.forEach(d -> {
            d.setPayrollStatus(PayrollStatus.APPROVED);
            payrollReportDetailRepo.save(d);
        });
    }

    @Async
    public void updateDetailStatusToPendingAsync(String summaryId) {
        List<PayrollReportDetail> details = payrollReportDetailRepo.findPayrollReportDetailBySummaryId(summaryId);
        details.forEach(d -> {
            d.setPayrollSimulation(false);
            d.setPayrollStatus(PayrollStatus.PENDING);
            payrollReportDetailRepo.save(d);
        });
    }

    @Async
    public void updateEmployeeLoanAsync(String summaryId, String companyId) {
        List<PayrollReportDetail> payrollReportDetails = payrollReportDetailRepo.findPayrollReportDetailBySummaryId(summaryId);

        payrollReportDetails.stream()
                .map(ReportUtils::transform)
                .filter(x -> !x.getDetail().getReport().getPaymentSettings().isEmpty())
                .flatMap(x -> x.getDetail().getReport().getPaymentSettings().stream())
                .filter(x -> x.getType().equals(PaymentTypeEnum.DEDUCTION_MONTHLY))
                .map(x -> loanService.getEmployeeActiveLoan(companyId, x.getEmployeeID(), x.getName()))
                .forEach(loan -> {
                    RepaymentRequest req = new RepaymentRequest();
                    req.setAmount(loan.getScheduledRepaymentAmount());
                    req.setReference("being personal deduction for " + loan.getDescription());
                    loanService.recordRepayment(loan.getId(), req);
                });
    }

    @Async
    public void saveReportDetails(PaymentComputeResponse paymentComputeResponse, String companyId, String previousSummaryId) {
        long started = System.currentTimeMillis();
        List<PaymentInfo> paymentInfoList = Optional.ofNullable(paymentComputeResponse.getReport())
                .orElse(Collections.emptyList());
        if (paymentInfoList.isEmpty()) {
            LOGGER.warn("No payment info to persist for companyId={}", companyId);
            return;
        }

        String summaryId = String.valueOf(paymentComputeResponse.getId());
        PayrollStatus status = paymentComputeResponse.isPayrollSimulation()
                ? PayrollStatus.SIMULATED
                : PayrollStatus.INITIATED;
        LocalDateTime createdAt = LocalDateTime.now();

        // Collapse duplicate employee rows from compute in-memory (avoids per-row DB lookups).
        Map<String, PaymentInfo> byEmployee = new LinkedHashMap<>();
        for (PaymentInfo info : paymentInfoList) {
            if (info == null || info.getEmployeeID() == null || info.getEmployeeID().isBlank()) {
                continue;
            }
            byEmployee.merge(info.getEmployeeID(), info, this::mergePaymentInfoPair);
        }

        // One query for existing rows on this summary (normally empty for a new run).
        Map<String, PayrollReportDetail> existingByEmployee = payrollReportDetailRepo
                .findPayrollReportDetailBySummaryId(summaryId)
                .stream()
                .filter(d -> d.getEmployeeId() != null)
                .collect(Collectors.toMap(
                        PayrollReportDetail::getEmployeeId,
                        d -> d,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        List<PayrollReportDetail> toInsert = new ArrayList<>(byEmployee.size());
        List<PayrollReportDetail> toUpdate = new ArrayList<>();

        for (PaymentInfo x : byEmployee.values()) {
            try {
                PayrollReportDetail existingReport = existingByEmployee.get(x.getEmployeeID());
                PaymentInfo paymentInfoToSave = x;

                if (existingReport != null) {
                    PaymentInfo oldPaymentInfo = Optional.ofNullable(ReportUtils.transform(existingReport))
                            .map(ReportResponse::getDetail)
                            .map(PayComputeDetailResponse::getReport)
                            .orElse(new PaymentInfo());
                    boolean mapsDifferent = !Objects.equals(oldPaymentInfo.getPayeeTax(), x.getPayeeTax())
                            && !Objects.equals(oldPaymentInfo.getPension(), x.getPension());
                    if (mapsDifferent) {
                        oldPaymentInfo.setGrossPay(mergeMaps(oldPaymentInfo.getGrossPay(), paymentInfoToSave.getGrossPay()));
                        oldPaymentInfo.setDeduction(mergeMaps(oldPaymentInfo.getDeduction(), x.getDeduction()));
                        oldPaymentInfo.setTaxRelief(mergeMaps(oldPaymentInfo.getTaxRelief(), x.getTaxRelief()));
                        oldPaymentInfo.setPayeeTax(mergeMaps(oldPaymentInfo.getPayeeTax(), x.getPayeeTax()));
                        oldPaymentInfo.setEarning(mergeMaps(oldPaymentInfo.getEarning(), x.getEarning()));
                        oldPaymentInfo.setNhf(mergeMaps(oldPaymentInfo.getNhf(), x.getNhf()));
                        oldPaymentInfo.setOthers(mergeMaps(oldPaymentInfo.getOthers(), x.getOthers()));
                        oldPaymentInfo.setPension(mergeMaps(oldPaymentInfo.getPension(), x.getPension()));
                        oldPaymentInfo.setNetPay(
                                Optional.ofNullable(oldPaymentInfo.getNetPay()).orElse(BigDecimal.ZERO)
                                        .add(Optional.ofNullable(paymentInfoToSave.getNetPay()).orElse(BigDecimal.ZERO)));
                        paymentInfoToSave = oldPaymentInfo;
                    }
                }

                PayComputeDetailResponse payComputeDetailResponse = PayComputeDetailResponse.builder()
                        .report(paymentInfoToSave)
                        .build();

                PayrollReportDetail payrollReportDetail = existingReport != null
                        ? existingReport
                        : PayrollReportDetail.builder().id(UUID.randomUUID().toString()).build();

                payrollReportDetail.setEmployeeId(paymentInfoToSave.getEmployeeID());
                payrollReportDetail.setFullName(Optional.ofNullable(paymentInfoToSave.getFullName()).orElse("Unknown"));
                payrollReportDetail.setSummaryId(summaryId);
                payrollReportDetail.setCurrency(paymentInfoToSave.getCurrency() != null
                        ? paymentInfoToSave.getCurrency().getCode()
                        : null);
                payrollReportDetail.setExchangeInfo(Optional.ofNullable(paymentInfoToSave.getExchangeInfo()).orElse(null));
                payrollReportDetail.setCompanyId(companyId);
                payrollReportDetail.setOffCycleId(paymentComputeResponse.getOffCycleId());
                payrollReportDetail.setDepartmentId(paymentInfoToSave.getDepartmentID());
                payrollReportDetail.setStartDate(paymentInfoToSave.getStartDate());
                payrollReportDetail.setEndDate(paymentInfoToSave.getEndDate());
                payrollReportDetail.setReport(ReportUtils.serializeResponse(payComputeDetailResponse));
                payrollReportDetail.setCreatedDate(createdAt);
                payrollReportDetail.setPayrollSimulation(paymentComputeResponse.isPayrollSimulation());
                payrollReportDetail.setPayrollStatus(status);
                payrollReportDetail.setOffCycle(paymentComputeResponse.isOffCycle());

                if (existingReport != null) {
                    toUpdate.add(payrollReportDetail);
                } else {
                    toInsert.add(payrollReportDetail);
                }
            } catch (Exception e) {
                LOGGER.error("Error building report detail for employeeId={} startDate={} endDate={}",
                        x.getEmployeeID(), x.getStartDate(), x.getEndDate(), e);
                throw e;
            }
        }

        bulkInsertDetails(toInsert);
        if (!toUpdate.isEmpty()) {
            for (int i = 0; i < toUpdate.size(); i += DETAIL_BULK_BATCH_SIZE) {
                int end = Math.min(i + DETAIL_BULK_BATCH_SIZE, toUpdate.size());
                payrollReportDetailRepo.saveAll(toUpdate.subList(i, end));
            }
        }

        LOGGER.info(
                "Persisted payroll details summaryId={} companyId={} inserted={} updated={} in {} ms",
                summaryId,
                companyId,
                toInsert.size(),
                toUpdate.size(),
                System.currentTimeMillis() - started
        );

        if (previousSummaryId != null) {
            PayCompteVarianceDetailsCustomized payComputeVarianceDetailsCustomized = PayCompteVarianceDetailsCustomized.builder()
                    .summaryDetailsVariance(processSummaryDetailsVarianceCustomized(paymentComputeResponse.getId(), UUID.fromString(previousSummaryId)))
                    .build();

            PayrollVarianceDetailsCustomized payrollVarianceDetailsCustomized = PayrollVarianceDetailsCustomized.builder()
                    .id(paymentComputeResponse.getId())
                    .summaryVarianceDetails(ReportUtils.serializeResponse(payComputeVarianceDetailsCustomized))
                    .build();
            payrollVarianceDetailsCustomizedRepo.save(payrollVarianceDetailsCustomized);
        }
    }

    private void bulkInsertDetails(List<PayrollReportDetail> details) {
        if (details == null || details.isEmpty()) {
            return;
        }
        for (int i = 0; i < details.size(); i += DETAIL_BULK_BATCH_SIZE) {
            int end = Math.min(i + DETAIL_BULK_BATCH_SIZE, details.size());
            List<PayrollReportDetail> chunk = details.subList(i, end);
            BulkOperations bulk = mongoTemplate.bulkOps(
                    BulkOperations.BulkMode.UNORDERED,
                    PayrollReportDetail.class
            );
            bulk.insert(chunk);
            bulk.execute();
        }
    }

    private PaymentInfo mergePaymentInfoPair(PaymentInfo a, PaymentInfo b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        a.setGrossPay(mergeMaps(a.getGrossPay(), b.getGrossPay()));
        a.setDeduction(mergeMaps(a.getDeduction(), b.getDeduction()));
        a.setTaxRelief(mergeMaps(a.getTaxRelief(), b.getTaxRelief()));
        a.setPayeeTax(mergeMaps(a.getPayeeTax(), b.getPayeeTax()));
        a.setEarning(mergeMaps(a.getEarning(), b.getEarning()));
        a.setNhf(mergeMaps(a.getNhf(), b.getNhf()));
        a.setOthers(mergeMaps(a.getOthers(), b.getOthers()));
        a.setPension(mergeMaps(a.getPension(), b.getPension()));
        BigDecimal netA = Optional.ofNullable(a.getNetPay()).orElse(BigDecimal.ZERO);
        BigDecimal netB = Optional.ofNullable(b.getNetPay()).orElse(BigDecimal.ZERO);
        a.setNetPay(netA.add(netB));
        return a;
    }
    @Async
    public void offLoadNewValuesToYTD(List<PayrollReportDetail>  payrollReportDetailList, String companyId, boolean isRollback) {
        try {
            Map<String, Map<String, BigDecimal>> newValuesForAllEmployees = new HashMap<>();
            Map<String, YTDReport> latestYTDs = new HashMap<>();
            payrollReportDetailList.stream()
                    .map(ReportUtils::transform)
                    .forEach(x -> {
                        Map<String, BigDecimal> newValuesForEmployee = new HashMap<>();

                        Map<String, BigDecimal> deduction = x.getDetail().getReport().getDeduction();
                        newValuesForEmployee.put(MapKeys.NATIONAL_HOUSING_FUND, deduction.get(MapKeys.NATIONAL_HOUSING_FUND) != null ? deduction.get(MapKeys.NATIONAL_HOUSING_FUND) : BigDecimal.ZERO);
                        newValuesForEmployee.put("PAYE TAX", deduction.get("PAYE TAX") != null ? deduction.get("PAYE TAX") : BigDecimal.ZERO);
                        newValuesForEmployee.put("WHT", deduction.get("WHT") != null ? deduction.get("WHT") : BigDecimal.ZERO);

                        for (String k : deduction.keySet()) {
                            k = k.replace(".", "-");
                            newValuesForEmployee.put(k + "-deduction-marker", deduction.get(k) != null ? deduction.get(k) : BigDecimal.ZERO);
                        }

                        Map<String, BigDecimal> grossPay = x.getDetail().getReport().getGrossPay();
                        newValuesForEmployee.put(MapKeys.BASIC_SALARY, grossPay.get(MapKeys.BASIC_SALARY));
                        newValuesForEmployee.put(MapKeys.GROSS_PAY, grossPay.get(MapKeys.GROSS_PAY));

                        Map<String, BigDecimal> pension = x.getDetail().getReport().getPension();
                        newValuesForEmployee.put(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION,  pension != null ? pension.get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION) : BigDecimal.ZERO);
                        newValuesForEmployee.put(MapKeys.EMPLOYER_PENSION_CONTRIBUTION,  pension != null ? pension.get(MapKeys.EMPLOYER_PENSION_CONTRIBUTION) : BigDecimal.ZERO);
                        newValuesForEmployee.put("Voluntary Pension Contribution", pension != null ? pension.get("Voluntary Pension Contribution") : BigDecimal.ZERO);

                        BigDecimal netPay = x.getDetail().getReport().getNetPay();
                        newValuesForEmployee.put(MapKeys.NET_PAY,  netPay);

                        Map<String, BigDecimal> taxRelief = x.getDetail().getReport().getTaxRelief();
                        newValuesForEmployee.put("Taxable Income", taxRelief.get("MONTHLY CHARGEABLE INCOME"));
                        newValuesForAllEmployees.put(x.getEmployeeId(), newValuesForEmployee);
                    });


            newValuesForAllEmployees.forEach((x,y) -> {
                Optional<YTDReport> ytdReportOptional = ytdReportRepo.findYTDReportByEmployeeIdAndCompanyId(x, companyId);
                YTDReport ytdReport;
                if (ytdReportOptional.isEmpty()) {
                    ytdReport = createYTDReportForNewEmployee(x, y, companyId);
                } else {
                    ytdReport = ytdReportOptional.get();
                    if (!isRollback) {
                        ytdReport.setBasicSalary(ytdReport.getBasicSalary().add(y.get(MapKeys.BASIC_SALARY)));
                        ytdReport.setGrossPay(ytdReport.getGrossPay().add(y.get(MapKeys.GROSS_PAY)));
                        ytdReport.setNetPay(ytdReport.getNetPay().add(y.get(MapKeys.NET_PAY)));
                        ytdReport.setNhf(ytdReport.getNhf().add(y.get(MapKeys.NATIONAL_HOUSING_FUND)));
                        ytdReport.setPayeeTax(ytdReport.getPayeeTax().add(y.get("PAYE TAX")));
                        ytdReport.setEmployerPension(y.get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION));
                        ytdReport.setEmployeePension(y.get(MapKeys.EMPLOYER_PENSION_CONTRIBUTION));
                        ytdReport.setVoluntarPensionContribution(y.get("Voluntary Pension Contribution"));
                        ytdReport.setPension(ytdReport.getPension().add(y.get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION))
                                .add(y.get(MapKeys.EMPLOYER_PENSION_CONTRIBUTION))
                                .add(y.get("Voluntary Pension Contribution")));
                        ytdReport.setTaxableIncome(ytdReport.getTaxableIncome().add(y.get("Taxable Income")));
                        ytdReport.setWht(ytdReport.getWht().add(y.get("WHT")));
                        ytdReport.setDeductions(mergeDeductionMap(ytdReport.getDeductions(), y));
                    } else {
                        ytdReport.setBasicSalary(ytdReport.getBasicSalary().subtract(y.get(MapKeys.BASIC_SALARY)));
                        ytdReport.setGrossPay(ytdReport.getGrossPay().subtract(y.get(MapKeys.GROSS_PAY)));
                        ytdReport.setNetPay(ytdReport.getNetPay().subtract(y.get(MapKeys.NET_PAY)));
                        ytdReport.setNhf(ytdReport.getNhf().subtract(y.get(MapKeys.NATIONAL_HOUSING_FUND)));
                        ytdReport.setPayeeTax(ytdReport.getPayeeTax().subtract(y.get("PAYE TAX")));
                        ytdReport.setEmployerPension(y.get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION));
                        ytdReport.setEmployeePension(y.get(MapKeys.EMPLOYER_PENSION_CONTRIBUTION));
                        ytdReport.setVoluntarPensionContribution(y.get("Voluntary Pension Contribution"));
                        ytdReport.setPension(ytdReport.getPension().subtract(y.get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION))
                                .add(y.get(MapKeys.EMPLOYER_PENSION_CONTRIBUTION))
                                .add(y.get("Voluntary Pension Contribution")));
                        ytdReport.setTaxableIncome(ytdReport.getTaxableIncome().subtract(y.get("Taxable Income")));
                        ytdReport.setWht(ytdReport.getWht().subtract(y.get("WHT")));
                        ytdReport.setDeductions(reverseDeductionMap(ytdReport.getDeductions(), y));
                    }
                }
                ytdReportRepo.save(ytdReport);
                latestYTDs.put(x, ytdReport);
            });

            payrollReportDetailList
                    .forEach(x -> {
                        PayrollReportDetail payrollReportDetail = payrollReportDetailRepo.findById(x.getId()).get();
                        if (!isRollback) {
                            YTDReport ytdReport = latestYTDs.get(payrollReportDetail.getEmployeeId());
                            Map<String, BigDecimal> ytdReportMap = new HashMap<>();
                            ytdReportMap.put(MapKeys.BASIC_SALARY, ytdReport.getBasicSalary());
                            ytdReportMap.put(MapKeys.GROSS_PAY, ytdReport.getGrossPay());
                            ytdReportMap.put(MapKeys.NET_PAY, ytdReport.getNetPay());
                            ytdReportMap.put(MapKeys.NATIONAL_HOUSING_FUND, ytdReport.getNhf());
                            ytdReportMap.put(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION, ytdReport.getEmployeePension());
                            ytdReportMap.put(MapKeys.EMPLOYER_PENSION_CONTRIBUTION, ytdReport.getEmployerPension());
                            ytdReportMap.put("Voluntary Pension Contribution", ytdReport.getVoluntarPensionContribution());
                            ytdReportMap.put("PAYE TAX", ytdReport.getPayeeTax());
                            ytdReportMap.put("Pension", ytdReport.getPension());
                            ytdReportMap.put("Taxable Income", ytdReport.getTaxableIncome());
                            ytdReportMap.put("WHT", ytdReport.getWht());

                            for (String k : ytdReport.getDeductions().keySet()) {
                                ytdReportMap.put(k, ytdReport.getDeductions().get(k));
                            }

                            PayComputeDetailResponse payComputeDetailResponse = SerializationUtils.deserialize(payrollReportDetail.getReport());
                            PaymentInfo paymentInfo = payComputeDetailResponse.getReport();
                            paymentInfo.setYtdReport(ytdReportMap);
                            payComputeDetailResponse.setReport(paymentInfo);
                            payrollReportDetail.setReport(ReportUtils.serializeResponse(payComputeDetailResponse));
                            payrollReportDetail.setPayrollStatus(!isRollback ? PayrollStatus.APPROVED : PayrollStatus.ROLLED_BACK);
                            payrollReportDetailRepo.save(payrollReportDetail);
                        }
                    });
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private YTDReport createYTDReportForNewEmployee(String employeeId, Map<String, BigDecimal> currentValues, String companyId) {
        return YTDReport.builder()
                .id(UUID.randomUUID().toString())
                .employeeId(employeeId)
                .companyId(companyId)
                .basicSalary(currentValues.get(MapKeys.BASIC_SALARY))
                .grossPay(currentValues.get(MapKeys.GROSS_PAY))
                .netPay(currentValues.get(MapKeys.NET_PAY))
                .nhf(currentValues.get(MapKeys.NATIONAL_HOUSING_FUND))
                .payeeTax(currentValues.get("PAYE TAX"))
                .employeePension(currentValues.get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION))
                .employerPension(currentValues.get(MapKeys.EMPLOYER_PENSION_CONTRIBUTION))
                .voluntarPensionContribution(currentValues.get("Voluntary Pension Contribution"))
                .pension(currentValues.get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION)
                        .add(currentValues.get(MapKeys.EMPLOYER_PENSION_CONTRIBUTION))
                        .add(currentValues.get("Voluntary Pension Contribution"))
                )
                .taxableIncome(currentValues.get("Taxable Income"))
                .wht(currentValues.get("WHT"))
                .deductions(extractDeductionMap(currentValues))
                .build();
    }

    private Map<String, BigDecimal> extractDeductionMap(Map<String, BigDecimal> currentValues) {
        Map<String, BigDecimal> mergedMap =  currentValues.entrySet()
                .stream()
                .filter(entry -> entry.getKey().contains("-deduction-marker"))
                .collect(Collectors.toMap(
                        entry -> entry.getKey().replace("-deduction-marker", ""),
                        Map.Entry::getValue
                ));

        BigDecimal totalDeduction = mergedMap.entrySet().stream().filter(x -> "Total Deduction".equalsIgnoreCase(x.getKey()))
                .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);

        mergedMap.put("Total Deduction", totalDeduction);
        return mergedMap;
    }

    private Map<String, BigDecimal> mergeDeductionMap(Map<String, BigDecimal> currentValues, Map<String, BigDecimal> newValues) {
        Map<String, BigDecimal> filteredNewValues = extractDeductionMap(newValues);
        Map<String, BigDecimal> mergedMap = new HashMap<>(currentValues);
        try {
            filteredNewValues.forEach((key, value) ->
                    mergedMap.merge(key, value, BigDecimal::add)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mergedMap;
    }

    private Map<String, BigDecimal> reverseDeductionMap(Map<String, BigDecimal> currentValues, Map<String, BigDecimal> newValues) {
        Map<String, BigDecimal> filteredNewValues = extractDeductionMap(newValues);
        Map<String, BigDecimal> resultMap = new HashMap<>(currentValues);
        filteredNewValues.forEach((key, value) -> {
            resultMap.computeIfPresent(key, (k, v) -> {
                BigDecimal newVal = v.subtract(value); // subtract
                return newVal.compareTo(BigDecimal.ZERO) == 0 ? null : newVal;
            });
        });
        return resultMap;
    }

    private Map<String, Map<String, BigDecimal>> processSummaryDetailsVarianceCustomized(UUID currentSummaryId , UUID previousSummaryId) {

        Map<String, Map<String, BigDecimal>> previous =
                extractCustomValues(String.valueOf(previousSummaryId));

        Map<String, Map<String, BigDecimal>> current =
                extractCustomValues(String.valueOf(currentSummaryId));

        Map<String, Map<String, BigDecimal>> varianceResult = new HashMap<>();

        for (Map.Entry<String, Map<String, BigDecimal>> entry : current.entrySet()) {

            String employeeId = entry.getKey();
            Map<String, BigDecimal> currentValues = entry.getValue();
            Map<String, BigDecimal> previousValues = previous.getOrDefault(employeeId, Collections.emptyMap());

            Map<String, BigDecimal> employeeVariance = calculateVariance(currentValues, previousValues);

            varianceResult.put(employeeId, employeeVariance);
        }

        return varianceResult;
    }

    private Map<String, BigDecimal> calculateVariance(
            Map<String, BigDecimal> current,
            Map<String, BigDecimal> previous) {

        Map<String, BigDecimal> variance = new HashMap<>();

        for (Map.Entry<String, BigDecimal> entry : current.entrySet()) {

            String metric = entry.getKey();
            BigDecimal currentValue = defaultZero(entry.getValue());
            BigDecimal previousValue = defaultZero(previous.get(metric));

            variance.put(metric, currentValue.subtract(previousValue));
        }
        return variance;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Map<String, Map<String, BigDecimal>> extractCustomValues(String reportId) {

        Map<String, Map<String, BigDecimal>> extractedValues = new HashMap<>();

        payrollReportDetailRepo.findPayrollReportDetailBySummaryId(reportId)
                .forEach(details -> {

                    ReportResponse response = ReportUtils.transform(details);
                    PaymentInfo report = response.getDetail().getReport();

                    Map<String, BigDecimal> values = new HashMap<>();

                    values.put(PayrollMetrics.GROSS_PAY,
                            defaultZero(report.getGrossPay().get("Gross Pay")));

                    values.put(PayrollMetrics.GROSS_SALARY,
                            defaultZero(report.getGrossPay().get("Gross Salary")));

                    values.put(PayrollMetrics.EMPLOYEE_PENSION,
                            defaultZero(report.getPension().get("Employee Pension Contribution")));

                    values.put(PayrollMetrics.NHF,
                            defaultZero(report.getNhf().get("National Housing Fund")));

                    values.put(PayrollMetrics.PAYE,
                            defaultZero(report.getPayeeTax().get("Monthly Paye")));

                    values.put(PayrollMetrics.PERFORMANCE_BONUS,
                            defaultZero(report.getGrossPay().get("Monthly Performance Bonus")));

                    values.put(PayrollMetrics.TOTAL_DEDUCTION,
                            defaultZero(report.getDeduction().get("Total Deduction")));

                    values.put(PayrollMetrics.NET_PAY,
                            defaultZero(report.getNetPay()));

                    extractedValues.put(response.getEmployeeId(), values);
                });

        return extractedValues;
    }

    @Async
    public void generatePayrollGLReport(PayrollReportSummary existingSummaryReport) {

        EmployeeFilterRequest employeeFilterRequest = new EmployeeFilterRequest();
        employeeFilterRequest.setCompanyID(existingSummaryReport.getCompanyId());

        Map<String, List<String>> costCenters =
                adminService.getCostCenterDetails(
                        employeeFilterRequest,
                        AuthUtil.getAuthentication().toString()
                );

        List<PayrollReportDetail> reportDetails =
                payrollReportDetailRepo.findPayrollReportDetailBySummaryId(
                        existingSummaryReport.getId().toString()
                );

        List<PaymentElementGLMapping> glMappings =
                paymentElementGLMappingCustomRepository.findAll();

        boolean hasAssignedEmployees = costCenters != null
                && costCenters.values().stream().anyMatch(ids -> ids != null && !ids.isEmpty());
        if (!hasAssignedEmployees) {
            List<String> allEmployeeIds = ReportUtils.transform(reportDetails).stream()
                    .map(employee -> employee.getEmployeeId())
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
            costCenters = new HashMap<>();
            costCenters.put("", allEmployeeIds);
        }

        // ONE map for ALL cost centers
        Map<String, GLSummary> gls = new HashMap<>();

        for (String costCenter : costCenters.keySet()) {

            List<String> employeeIds = costCenters.get(costCenter);

            List<PaymentInfo> paymentInfoList = ReportUtils.transform(reportDetails)
                    .stream()
                    .filter(employee -> employeeIds.contains(employee.getEmployeeId()))
                    .map(info -> info.getDetail().getReport())
                    .toList();

            paymentInfoList.stream()
                    .flatMap(paymentInfo -> paymentInfo.getPaymentSettings().stream())
                    .forEach(setting -> {

                        glMappings.stream()
                                .filter(mapping ->
                                        mapping.getPayElement()
                                                .equalsIgnoreCase(setting.getName()))
                                .findFirst()
                                .ifPresent(glMapping -> {

                                    BigDecimal amount = setting.getValue();

                                    addToGL(
                                            gls,
                                            glMapping.getGlCodeDebit(),
                                            amount,
                                            true,
                                            costCenter
                                    );

                                    addToGL(
                                            gls,
                                            glMapping.getGlCodeCredit(),
                                            amount,
                                            false,
                                            costCenter
                                    );
                                });
                    });

            paymentInfoList.forEach(setting -> {

                BigDecimal employerPension = setting.getPension().get(
                        MapKeys.EMPLOYER_PENSION_CONTRIBUTION
                );

                BigDecimal nstif = setting.getGrossPay()
                        .get(MapKeys.GROSS_PAY)
                        .multiply(BigDecimal.valueOf(0.01));

                BigDecimal itf = employerPension
                        .add(nstif)
                        .multiply(new BigDecimal("0.01"));

            addPaymentElementToGL(
                    gls,
                    glMappings,
                    "PAYE TAX",
//                    setting.getPayeeTax().get(MapKeys.PAYEE_TAX)
                    setting.getPayeeTax().get("PAYE"),
                    costCenter
            );

                addPaymentElementToGL(
                        gls,
                        glMappings,
                        "NET SALARY",
                        setting.getNetPay(),
                        costCenter
                );

                addPaymentElementToGL(
                        gls,
                        glMappings,
                        "ER PENSION",
                        employerPension,
                        costCenter
                );

                addPaymentElementToGL(
                        gls,
                        glMappings,
                        "MONTHLY EMPLOYEE PENSION @ 8%",
                        setting.getPension().get(
                                MapKeys.EMPLOYEE_PENSION_CONTRIBUTION
                        ),
                        costCenter
                );

                addPaymentElementToGL(
                        gls,
                        glMappings,
                        "MONTHLY NHF",
                        setting.getNhf().get(
                                MapKeys.NATIONAL_HOUSING_FUND
                        ),
                        costCenter
                );

                addPaymentElementToGL(
                        gls,
                        glMappings,
                        "NSITF",
                        nstif,
                        costCenter
                );

                addPaymentElementToGL(
                        gls,
                        glMappings,
                        "ITF",
                        itf,
                        costCenter
                );
            });
        }

        // SAVE ONLY ONCE
        PayrollGLReport payrollGLReport = PayrollGLReport.builder()
                .id(existingSummaryReport.getId().toString())
                .generated(LocalDateTime.now())
                .gls(gls)
                .status(GLReportStatus.GENERATED)
                .build();

        payrollGLReportRepository.save(payrollGLReport);
    }

    private void addToGL( Map<String, GLSummary> gls, String glCode, BigDecimal amount, boolean debit, String costCenter ) {
        if (glCode == null || amount == null) {
            return;
        }
        gls.compute(glCode, (key, existing) -> { if (existing == null) {

            return GLSummary.builder()
                    .glCode(glCode)
                    .debit(debit ? amount : BigDecimal.ZERO)
                    .credit(debit ? BigDecimal.ZERO : amount)
                    .function(costCenter)
                    .build(); }
            if (debit) {
                existing.setDebit(existing.getDebit().add(amount)); }
            else { existing.setCredit(existing.getCredit().add(amount));
            }  return existing; });
    }

    private void addPaymentElementToGL( Map<String, GLSummary> gls, List<PaymentElementGLMapping> glMappings, String paymentElement, BigDecimal amount, String costCenter ) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        glMappings.stream()
                .filter(mapping -> mapping.getPayElement().equalsIgnoreCase(paymentElement))
                .findFirst()
                .ifPresent(
                        glMapping -> { addToGL( gls, glMapping.getGlCodeDebit(), amount, true, costCenter );
                            addToGL( gls, glMapping.getGlCodeCredit(), amount, false, costCenter );
                        });
    }
}

package com.xykine.computation.service;


import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.entity.PayrollStatus;

import com.xykine.computation.entity.YTDReport;
import com.xykine.computation.repo.PayrollReportDetailRepo;

import com.xykine.computation.repo.YTDReportRepo;
import com.xykine.computation.request.RepaymentRequest;
import com.xykine.computation.response.PayComputeDetailResponse;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.utils.ReportUtils;
import lombok.RequiredArgsConstructor;

import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import org.xykine.payroll.model.MapKeys;
import org.xykine.payroll.model.PaymentInfo;
import org.xykine.payroll.model.enums.PaymentTypeEnum;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.xykine.computation.service.ReportPersistenceServiceImpl.mergeMaps;

@Service
@RequiredArgsConstructor
public class PayrollAsyncService {

    private final PayrollReportDetailRepo payrollReportDetailRepo;
    private final LoanService loanService;
    private final YTDReportRepo ytdReportRepo;

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
    public void saveReportDetails(PaymentComputeResponse paymentComputeResponse,
                                   String companyId) {
        List<PaymentInfo> paymentInfoList = Optional.ofNullable(paymentComputeResponse.getReport())
                .orElse(Collections.emptyList());

            paymentInfoList.forEach(x -> {
                try {
                    PayrollReportDetail existingReport =
                            payrollReportDetailRepo.findPayrollReportDetailByCompanyIdAndEmployeeIdAndStartDateAndEndDateAndSummaryId(
                                    companyId,
                                    x.getEmployeeID(),
                                    x.getStartDate(),
                                    x.getEndDate(),
                                    String.valueOf(paymentComputeResponse.getId()));

                    PaymentInfo paymentInfoToSave = x;
                    if (existingReport != null) {
                        // safely unwrap old report or create empty PaymentInfo
                        PaymentInfo oldPaymentInfo = Optional.ofNullable(ReportUtils.transform(existingReport))
                                .map(r -> r.getDetail())
                                .map(d -> d.getReport())
                                .orElse(new PaymentInfo());
                        //  merge maps safely
                        boolean mapsDifferent = !Objects.equals(oldPaymentInfo.getPayeeTax(), x.getPayeeTax()) && !Objects.equals(oldPaymentInfo.getPension(), x.getPension());
                        if (mapsDifferent) {
                            oldPaymentInfo.setGrossPay(mergeMaps(oldPaymentInfo.getGrossPay(), paymentInfoToSave.getGrossPay()));
                            oldPaymentInfo.setDeduction(mergeMaps(oldPaymentInfo.getDeduction(), x.getDeduction()));
                            oldPaymentInfo.setTaxRelief(mergeMaps(oldPaymentInfo.getTaxRelief(), x.getTaxRelief()));
                            oldPaymentInfo.setPayeeTax(mergeMaps(oldPaymentInfo.getPayeeTax(), x.getPayeeTax()));
                            oldPaymentInfo.setEarning(mergeMaps(oldPaymentInfo.getEarning(), x.getEarning()));
                            oldPaymentInfo.setNhf(mergeMaps(oldPaymentInfo.getNhf(), x.getNhf()));
                            oldPaymentInfo.setOthers(mergeMaps(oldPaymentInfo.getOthers(), x.getOthers()));
                            oldPaymentInfo.setPension(mergeMaps(oldPaymentInfo.getPension(), x.getPension()));
                            oldPaymentInfo.setNetPay(oldPaymentInfo.getNetPay().add(paymentInfoToSave.getNetPay()));
                            paymentInfoToSave = oldPaymentInfo;
                        }
                    }

                    PayComputeDetailResponse payComputeDetailResponse = PayComputeDetailResponse.builder()
                            .report(paymentInfoToSave)
                            .build();

                    // update existing report in-place or create new if null
                    PayrollReportDetail payrollReportDetail = existingReport != null ? existingReport :
                            PayrollReportDetail.builder()
                                    .id(UUID.randomUUID().toString())
                                    .build();

                    payrollReportDetail.setEmployeeId(paymentInfoToSave.getEmployeeID());
                    payrollReportDetail.setFullName(Optional.ofNullable(paymentInfoToSave.getFullName()).orElse("Unknown"));
                    payrollReportDetail.setSummaryId(String.valueOf(paymentComputeResponse.getId()));
                    payrollReportDetail.setCurrency(paymentInfoToSave.getCurrency() != null ?
                            paymentInfoToSave.getCurrency().getCode() : null);
                    payrollReportDetail.setExchangeInfo(Optional.ofNullable(paymentInfoToSave.getExchangeInfo()).orElse(null));
                    payrollReportDetail.setCompanyId(companyId);
                    payrollReportDetail.setOffCycleId(paymentComputeResponse.getOffCycleId());
                    payrollReportDetail.setDepartmentId(paymentInfoToSave.getDepartmentID());
                    payrollReportDetail.setStartDate(paymentInfoToSave.getStartDate());
                    payrollReportDetail.setEndDate(paymentInfoToSave.getEndDate());
                    payrollReportDetail.setReport(ReportUtils.serializeResponse(payComputeDetailResponse));
                    payrollReportDetail.setCreatedDate(LocalDateTime.now());
                    payrollReportDetail.setPayrollSimulation(paymentComputeResponse.isPayrollSimulation());
                    payrollReportDetail.setPayrollStatus(paymentComputeResponse.isPayrollSimulation() ? PayrollStatus.SIMULATED : PayrollStatus.INITIATED);
                    payrollReportDetail.setOffCycle(paymentComputeResponse.isOffCycle());
                    payrollReportDetailRepo.save(payrollReportDetail);

                } catch (Exception e) {
                    LOGGER.error("Error processing report for employeeId={} startDate={} endDate={}",
                            x.getEmployeeID(), x.getStartDate(), x.getEndDate(), e);
                    throw e; // rethrow so CompletableFuture sees the error
                }
            });
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
}

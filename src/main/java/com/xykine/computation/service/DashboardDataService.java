package com.xykine.computation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.xykine.computation.entity.*;
import com.xykine.computation.repo.DashboardGraphRepo;
import com.xykine.computation.repo.PayrollReportDetailRepo;
import com.xykine.computation.repo.YTDReportRepo;
import com.xykine.computation.response.DashboardCardResponse;
import com.xykine.computation.response.DashboardGraphResponse;
import com.xykine.computation.response.PayComputeDetailResponse;
import com.xykine.computation.utils.ComputationUtils;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import org.xykine.payroll.model.MapKeys;
import org.xykine.payroll.model.PaymentFrequencyEnum;

import com.xykine.computation.repo.DashboardCardRepo;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.utils.ReportUtils;
import org.xykine.payroll.model.PaymentInfo;


@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardDataService {

    private final DashboardCardRepo dashboardCardRepo;
    private final DashboardGraphRepo dashboardGraphRepo;
    private final YTDReportRepo ytdReportRepo;
    private final PayrollReportDetailRepo payrollReportDetailRepo;

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCalculatorImpl.class);

    public void updatePayrollCountTypeOffCycle(PayrollReportSummary payrollReportSummary) {
        DashboardCard dashboardCard;
        Optional<DashboardCard> dashboardCardOptional = dashboardCardRepo.findByCompanyId(payrollReportSummary.getCompanyId());
        if (dashboardCardOptional.isEmpty()) {
            dashboardCard = saveFreshDashboardCard(payrollReportSummary.getCompanyId());
        } else {
            dashboardCard = dashboardCardOptional.get();
        }

        long currentCount = dashboardCard.getTotalOffCyclePayroll();
        dashboardCard.setTotalOffCyclePayroll(++currentCount);
        updateDashboardData(dashboardCard, payrollReportSummary);
    }

    public void updatePayrollCountTypeRegular(PayrollReportSummary payrollReportSummary) {
        DashboardCard dashboardCard;
        Optional<DashboardCard> dashboardCardOptional = dashboardCardRepo.findByCompanyId(payrollReportSummary.getCompanyId());

        if (dashboardCardOptional.isEmpty()) {
            dashboardCard = saveFreshDashboardCard(payrollReportSummary.getCompanyId());
        } else {
            dashboardCard = dashboardCardOptional.get();
        }

        long currentCount = dashboardCard.getTotalRegularPayroll();
        dashboardCard.setTotalRegularPayroll(++currentCount);
        updateDashboardData(dashboardCard, payrollReportSummary);
    }

    public DashboardCardResponse retrieveDashboardCardData(String companyId){
        DashboardCard dashboardCard =  dashboardCardRepo.findByCompanyId(companyId).get();
        return DashboardCardResponse.builder()
                .totalOffCyclePayroll(dashboardCard.getTotalOffCyclePayroll())
                .totalRegularPayroll(dashboardCard.getTotalRegularPayroll())
                .totalPayrollCost(dashboardCard.getTotalPayrollCost())
                .averageEmployeeCost(dashboardCard.getAverageEmployeeCost())
                .lastUpdatedAt(dashboardCard.getLastUpdatedAt().toString())
                .build();
    }

    public Map<String, Object> getDashboardGraph(PaymentFrequencyEnum paymentFrequencyEnum, String companyId, int page, int size) {
        Pageable paging = PageRequest.of(page, size);
        Page<DashboardGraph> dashboardGraphs = dashboardGraphRepo.findDashboardGraphByPaymentFrequencyAndCompanyIdOrderByDateAddedDesc(paymentFrequencyEnum, companyId, paging);
        List<DashboardGraph> dashboardGraphList = dashboardGraphs.getContent();
        List<DashboardGraphResponse> dashboardResponse = ReportUtils.transformToResponse(dashboardGraphList);

        Map<String, Object> response = new HashMap<>();
        response.put("payrollDetails", dashboardResponse);
        response.put("currentPage", dashboardGraphs.getNumber());
        response.put("totalItems", dashboardGraphs.getTotalElements());
        response.put("totalPages", dashboardGraphs.getTotalPages());
        return response;
    }

    public void updateYTDReport(String id, String companyId) {
        List<PayrollReportDetail>  payrollReportDetailList = payrollReportDetailRepo.findPayrollReportDetailBySummaryId(id);
        breakJobsAndOffLoad(payrollReportDetailList, companyId);
    }

    private void breakJobsAndOffLoad(List<PayrollReportDetail>  payrollReportDetailList, String companyId) {
        int size = payrollReportDetailList.size();
        List<PayrollReportDetail> job1 = new ArrayList<>();
        List<PayrollReportDetail> job2 = new ArrayList<>();

        job1.addAll(payrollReportDetailList.subList(0, size/2));
        job2.addAll(payrollReportDetailList.subList(size/2, size));

        Executor executor1 = Executors.newFixedThreadPool(10);
        CompletableFuture.supplyAsync(() -> {
            return  offLoadNewValuesToYTD(job1, companyId);
        }, executor1);

        Executor executor2 = Executors.newFixedThreadPool(10);
        CompletableFuture.supplyAsync(() -> {
            return  offLoadNewValuesToYTD(job2, companyId);
        }, executor2);
    }

    private boolean offLoadNewValuesToYTD(List<PayrollReportDetail>  payrollReportDetailList, String companyId) {
        Map<String, Map<String, BigDecimal>> newValuesForAllEmployees = new HashMap<>();
        Map<String, YTDReport> latestYTDs = new HashMap<>();
        payrollReportDetailList.stream()
                .map(x -> ReportUtils.transform(x))
                .forEach(x -> {
                    Map<String, BigDecimal> newValuesForEmployee = new HashMap<>();

                    Map<String, BigDecimal> deduction = x.getDetail().getReport().getDeduction();
                    newValuesForEmployee.put(MapKeys.NATIONAL_HOUSING_FUND, deduction.get(MapKeys.NATIONAL_HOUSING_FUND));
                    newValuesForEmployee.put(MapKeys.PAYEE_TAX, deduction.get(MapKeys.PAYEE_TAX));

                    Map<String, BigDecimal> grossPay = x.getDetail().getReport().getGrossPay();
                    newValuesForEmployee.put(MapKeys.BASIC_SALARY, grossPay.get(MapKeys.BASIC_SALARY));
                    newValuesForEmployee.put(MapKeys.GROSS_PAY, grossPay.get(MapKeys.GROSS_PAY));

                    Map<String, BigDecimal> pension = x.getDetail().getReport().getPension();
                    newValuesForEmployee.put(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION,  pension.get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION));
                    newValuesForEmployee.put(MapKeys.EMPLOYER_PENSION_CONTRIBUTION,  pension.get(MapKeys.EMPLOYER_PENSION_CONTRIBUTION));

                    BigDecimal netPay = x.getDetail().getReport().getNetPay();
                    newValuesForEmployee.put(MapKeys.NET_PAY,  netPay);
                    newValuesForAllEmployees.put(x.getEmployeeId(), newValuesForEmployee);
                });

        newValuesForAllEmployees.forEach((x,y) -> {
            Optional<YTDReport> ytdReportOptional = ytdReportRepo.findYTDReportByEmployeeIdAndCompanyId(x, companyId);
            YTDReport ytdReport;
            if (ytdReportOptional.isEmpty()) {
                ytdReport = createYTDReportForNewEmployee(x, y, companyId);
            } else {
                ytdReport = ytdReportOptional.get();
                ytdReport.setBasicSalary(ytdReport.getBasicSalary().add(y.get(MapKeys.BASIC_SALARY)));
                ytdReport.setGrossPay(ytdReport.getGrossPay().add(y.get(MapKeys.GROSS_PAY)));
                ytdReport.setNetPay(ytdReport.getNetPay().add(y.get(MapKeys.NET_PAY)));
                ytdReport.setNhf(ytdReport.getNhf().add(y.get(MapKeys.NATIONAL_HOUSING_FUND)));
                ytdReport.setPayeeTax(ytdReport.getPayeeTax().add(y.get(MapKeys.PAYEE_TAX)));
                ytdReport.setEmployeeContributedPension(ytdReport.getEmployeeContributedPension().add((y.get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION))));
                ytdReport.setEmployerContributedPension(ytdReport.getEmployerContributedPension().add(y.get(MapKeys.EMPLOYER_PENSION_CONTRIBUTION)));
            }
            ytdReportRepo.save(ytdReport);
            latestYTDs.put(x, ytdReport);
        });

        payrollReportDetailList.stream()
                .forEach(x -> {
                    PayrollReportDetail payrollReportDetail = payrollReportDetailRepo.findById(x.getId()).get();
                    YTDReport ytdReport = latestYTDs.get(payrollReportDetail.getEmployeeId());
                    Map<String, BigDecimal> ytdReportMap = new HashMap<>();
                    ytdReportMap.put(MapKeys.BASIC_SALARY, ytdReport.getBasicSalary());
                    ytdReportMap.put(MapKeys.GROSS_PAY, ytdReport.getGrossPay());
                    ytdReportMap.put(MapKeys.NET_PAY, ytdReport.getNetPay());
                    ytdReportMap.put(MapKeys.NATIONAL_HOUSING_FUND, ytdReport.getNhf());
                    ytdReportMap.put(MapKeys.PAYEE_TAX, ytdReport.getPayeeTax());
                    ytdReportMap.put(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION, ytdReport.getEmployeeContributedPension());
                    ytdReportMap.put(MapKeys.EMPLOYER_PENSION_CONTRIBUTION, ytdReport.getEmployerContributedPension());

                    PayComputeDetailResponse payComputeDetailResponse = SerializationUtils.deserialize(payrollReportDetail.getReport());
                    PaymentInfo paymentInfo = payComputeDetailResponse.getReport();
                    paymentInfo.setYtdReport(ytdReportMap);

                    payrollReportDetail.setReport(ReportUtils.serializeResponse(payComputeDetailResponse));
                            payrollReportDetailRepo.save(payrollReportDetail);
                });
        return true;
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
                .payeeTax(currentValues.get(MapKeys.PAYEE_TAX))
                .employeeContributedPension(currentValues.get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION))
                .employerContributedPension(currentValues.get(MapKeys.EMPLOYER_PENSION_CONTRIBUTION))
                .build();
    }

    private void updateDashboardData(DashboardCard dashboardCard, PayrollReportSummary payrollReportSummary) {
        BigDecimal netPay = extractNetPayFromReport(payrollReportSummary);
        BigDecimal currentNetPay = dashboardCard.getTotalPayrollCost();
        dashboardCard.setTotalPayrollCost(currentNetPay.add(netPay));
        dashboardCard.setAverageEmployeeCost(ComputationUtils.roundToTwoDecimalPlaces(
                currentNetPay.add(netPay)
                        .divide(BigDecimal.valueOf(payrollReportSummary.getTotalNumberOfEmployees()), 2, RoundingMode.HALF_UP)
        ));
        dashboardCardRepo.save(dashboardCard);
        DashboardGraph dashboardGraph = DashboardGraph.builder()
                .id(UUID.randomUUID().toString())
                .companyId(payrollReportSummary.getCompanyId())
                .startDate(payrollReportSummary.getStartDate().toString())
                .endDate(payrollReportSummary.getEndDate().toString())
                .paymentFrequency(payrollReportSummary.getPaymentFrequency())
                .netPay(netPay)
                .dateAdded(LocalDateTime.now())
                .build();
        dashboardGraphRepo.save(dashboardGraph);
        updateYTDReport(payrollReportSummary.getId().toString(), payrollReportSummary.getCompanyId());
    }

    private BigDecimal extractNetPayFromReport(PayrollReportSummary payrollReportSummary){
        ReportResponse reportResponse = ReportUtils.transform(payrollReportSummary);
        return reportResponse.getSummary().getSummary().get(MapKeys.TOTAL_NET_PAY);
    }

    private DashboardCard saveFreshDashboardCard(String companyId){
        DashboardCard dashboardCard =  DashboardCard.builder()
                .id(UUID.randomUUID().toString())
                .companyId(companyId)
                .totalOffCyclePayroll(0L)
                .totalRegularPayroll(0L)
                .totalPayrollCost(BigDecimal.ZERO)
                .averageEmployeeCost(BigDecimal.ZERO)
                .lastUpdatedAt(LocalDateTime.now())
                .build();
        dashboardCardRepo.save(dashboardCard);
        return dashboardCard;
    }
}


package com.xykine.computation.service;

import java.math.BigDecimal;
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
import com.xykine.computation.utils.ComputationUtils;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

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
import com.xykine.computation.utils.AppConstants;
import com.xykine.computation.utils.ReportUtils;


@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardDataService {

    private final DashboardCardRepo dashboardCardRepo;
    private final DashboardGraphRepo dashboardGraphRepo;
    private final YTDReportRepo ytdReportRepo;
    private final PayrollReportDetailRepo payrollReportDetailRepo;

    public void updatePayrollCountTypeOffCycle(PayrollReportSummary payrollReportSummary) {
        DashboardCard dashboardCard = dashboardCardRepo.findByTableMarker(AppConstants.dashboardData).get();
        long currentCount = dashboardCard.getTotalOffCyclePayroll();
        dashboardCard.setTotalOffCyclePayroll(++currentCount);
        updateDashboardData(dashboardCard, payrollReportSummary);
    }

    public void updatePayrollCountTypeRegular(PayrollReportSummary payrollReportSummary) {
        DashboardCard dashboardCard = dashboardCardRepo.findByTableMarker(AppConstants.dashboardData).get();
        long currentCount = dashboardCard.getTotalRegularPayroll();
        dashboardCard.setTotalRegularPayroll(++currentCount);
        updateDashboardData(dashboardCard, payrollReportSummary);
    }

    public DashboardCardResponse retrieveDashboardData(){
        DashboardCard dashboardCard =  dashboardCardRepo.findByTableMarker(AppConstants.dashboardData).get();
        return DashboardCardResponse.builder()
                .totalOffCyclePayroll(dashboardCard.getTotalOffCyclePayroll())
                .totalRegularPayroll(dashboardCard.getTotalRegularPayroll())
                .totalPayrollCost(dashboardCard.getTotalPayrollCost())
                .averageEmployeeCost(dashboardCard.getAverageEmployeeCost())
                .lastUpdatedAt(dashboardCard.getLastUpdatedAt().toString())
                .build();
    }

    public Map<String, Object> getDashboardGraph(PaymentFrequencyEnum paymentFrequencyEnum, int page, int size) {
        Pageable paging = PageRequest.of(page, size);
        Page<DashboardGraph> dashboardGraphs = dashboardGraphRepo.findDashboardGraphByPaymentFrequencyOrderByDateAddedDesc(paymentFrequencyEnum, paging);
        List<DashboardGraph> dashboardGraphList = dashboardGraphs.getContent();
        List<DashboardGraphResponse> dashboardResponse = ReportUtils.transformToResponse(dashboardGraphList);

        Map<String, Object> response = new HashMap<>();
        response.put("payrollDetails", dashboardResponse);
        response.put("currentPage", dashboardGraphs.getNumber());
        response.put("totalItems", dashboardGraphs.getTotalElements());
        response.put("totalPages", dashboardGraphs.getTotalPages());
        return response;
    }

    public void updateYTDReport(String id) {
        List<PayrollReportDetail>  payrollReportDetailList = payrollReportDetailRepo.findPayrollReportDetailBySummaryId(id);
        List<ReportResponse> reportResponses = ReportUtils.transform(payrollReportDetailList);
        breakJobsAndOffLoad(reportResponses);
    }

    private void breakJobsAndOffLoad(List<ReportResponse> rawInfo) {
        int size = rawInfo.size();
        List<ReportResponse> job1 = new ArrayList<>();
        List<ReportResponse> job2 = new ArrayList<>();

        job1.addAll(rawInfo.subList(0, size/2));
        job2.addAll(rawInfo.subList(size/2, size));

        Executor executor1 = Executors.newFixedThreadPool(10);
        CompletableFuture.supplyAsync(() -> {
            return  offLoadNewValuesToYTD(job1);
        }, executor1);

        Executor executor2 = Executors.newFixedThreadPool(10);
        CompletableFuture.supplyAsync(() -> {
            return  offLoadNewValuesToYTD(job2);
        }, executor2);
    }

    private boolean offLoadNewValuesToYTD(List<ReportResponse> reportResponses) {
        Map<String, Map<String, Object>> newValuesForAllEmployees = new HashMap<>();
        reportResponses.stream()
                .forEach(x -> {
                    Map<String, Object> newValuesForEmployee = new HashMap<>();

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
                    newValuesForEmployee.put("companyId",  x.getCompanyId());

                    newValuesForAllEmployees.put(x.getEmployeeId(), newValuesForEmployee);
                });

        newValuesForAllEmployees.forEach((x,y) -> {
            Optional<YTDReport> ytdReportOptional = ytdReportRepo.findYTDReportByEmployeeIdAndCompanyId(x, (String) y.get("companyId"));
            if (ytdReportOptional.isEmpty()) {
                ytdReportRepo.save(createYTDReportForNewEmployee(x, y));
            } else {
                YTDReport ytdReport = ytdReportOptional.get();
                ytdReport.setBasicSalary(ytdReport.getBasicSalary().add((BigDecimal)y.get(MapKeys.BASIC_SALARY)));
                ytdReport.setGrossPay(ytdReport.getGrossPay().add((BigDecimal)y.get(MapKeys.GROSS_PAY)));
                ytdReport.setNetPay(ytdReport.getNetPay().add((BigDecimal)y.get(MapKeys.NET_PAY)));
                ytdReport.setNhf(ytdReport.getNhf().add((BigDecimal)y.get(MapKeys.NATIONAL_HOUSING_FUND)));
                ytdReport.setPayeeTax(ytdReport.getPayeeTax().add((BigDecimal)y.get(MapKeys.PAYEE_TAX)));
                ytdReport.setEmployeeContributedPension(ytdReport.getEmployeeContributedPension().add((BigDecimal)y.get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION)));
                ytdReport.setEmployerContributedPension(ytdReport.getEmployerContributedPension().add((BigDecimal)y.get(MapKeys.EMPLOYER_PENSION_CONTRIBUTION)));
                ytdReportRepo.save(ytdReport);
            }
        });
        return true;
    }

    private YTDReport createYTDReportForNewEmployee(String employeeId, Map<String, Object> currentValues) {
        return YTDReport.builder()
                .id(UUID.randomUUID().toString())
                .employeeId(employeeId)
                .companyId((String)currentValues.get("companyId"))
                .basicSalary((BigDecimal)currentValues.get(MapKeys.BASIC_SALARY))
                .grossPay((BigDecimal)currentValues.get(MapKeys.GROSS_PAY))
                .netPay((BigDecimal)currentValues.get(MapKeys.NET_PAY))
                .nhf((BigDecimal)currentValues.get(MapKeys.NATIONAL_HOUSING_FUND))
                .payeeTax((BigDecimal)currentValues.get(MapKeys.PAYEE_TAX))
                .employeeContributedPension((BigDecimal)currentValues.get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION))
                .employerContributedPension((BigDecimal)currentValues.get(MapKeys.EMPLOYER_PENSION_CONTRIBUTION))
                .build();
    }

    private void updateDashboardData(DashboardCard dashboardCard, PayrollReportSummary payrollReportSummary) {
        BigDecimal netPay = extractNetPayFromReport(payrollReportSummary);
        BigDecimal currentNetPay = dashboardCard.getTotalPayrollCost();
        dashboardCard.setTotalPayrollCost(currentNetPay.add(netPay));
        dashboardCard.setAverageEmployeeCost(ComputationUtils.roundToTwoDecimalPlaces(currentNetPay.add(netPay)
                .divide(BigDecimal.valueOf(payrollReportSummary.getTotalNumberOfEmployees()))));
        dashboardCardRepo.save(dashboardCard);
        DashboardGraph dashboardGraph = DashboardGraph.builder()
                .id(UUID.randomUUID().toString())
                .startDate(payrollReportSummary.getStartDate().toString())
                .endDate(payrollReportSummary.getEndDate().toString())
                .paymentFrequency(payrollReportSummary.getPaymentFrequency())
                .netPay(netPay)
                .dateAdded(LocalDateTime.now())
                .build();
        dashboardGraphRepo.save(dashboardGraph);
        updateYTDReport(payrollReportSummary.getId().toString());
    }



    private BigDecimal extractNetPayFromReport(PayrollReportSummary payrollReportSummary){
        ReportResponse reportResponse = ReportUtils.transform(payrollReportSummary);
        return reportResponse.getSummary().getSummary().get(MapKeys.TOTAL_NET_PAY);
    }

}


package com.xykine.computation.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import com.xykine.computation.entity.DashboardGraph;
import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.repo.DashboardGraphRepo;
import com.xykine.computation.response.DashboardCardResponse;
import com.xykine.computation.response.DashboardGraphResponse;
import com.xykine.computation.utils.ComputationUtils;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.xykine.payroll.model.AuditTrailEvents;
import org.xykine.payroll.model.MapKeys;

import com.xykine.computation.entity.DashboardCard;
import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.repo.DashboardCardRepo;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.utils.AppConstants;
import com.xykine.computation.utils.ReportUtils;
import org.xykine.payroll.model.PaymentFrequencyEnum;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardDataService {

    private final DashboardCardRepo dashboardCardRepo;
    private final DashboardGraphRepo dashboardGraphRepo;

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
    }

    private BigDecimal extractNetPayFromReport(PayrollReportSummary payrollReportSummary){
        ReportResponse reportResponse = ReportUtils.transform(payrollReportSummary);
        return reportResponse.getSummary().getSummary().get(MapKeys.TOTAL_NET_PAY);
    }

}

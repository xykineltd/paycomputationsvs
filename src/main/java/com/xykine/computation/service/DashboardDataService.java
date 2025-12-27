package com.xykine.computation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.xykine.computation.entity.*;
import com.xykine.computation.repo.DashboardGraphRepo;
import com.xykine.computation.repo.PayrollReportDetailRepo;
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
import com.xykine.computation.utils.ReportUtils;


@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardDataService {

    private final DashboardCardRepo dashboardCardRepo;
    private final DashboardGraphRepo dashboardGraphRepo;
    private final PayrollReportDetailRepo payrollReportDetailRepo;
    private final PayrollAsyncService payrollAsyncService;

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCalculatorImpl.class);

    private final Executor executor = Executors.newFixedThreadPool(10);

    public void updatePayrollCountTypeOffCycle(PayrollReportSummary payrollReportSummary, boolean isRollback ) {
        DashboardCard dashboardCard;
        Optional<DashboardCard> dashboardCardOptional = dashboardCardRepo.findByCompanyId(payrollReportSummary.getCompanyId());
        dashboardCard = dashboardCardOptional.orElseGet(() -> saveFreshDashboardCard(payrollReportSummary.getCompanyId()));

        long currentCount = dashboardCard.getTotalOffCyclePayroll();
        if (!isRollback) {
            dashboardCard.setTotalOffCyclePayroll(Math.max(++currentCount, 0));
        } else {
            dashboardCard.setTotalOffCyclePayroll(Math.max(--currentCount, 0));
        }
        updateDashboardData(dashboardCard, payrollReportSummary, isRollback);
    }

    public void updatePayrollCountTypeRegular(PayrollReportSummary payrollReportSummary, boolean isRollBack) {
        DashboardCard dashboardCard;
        Optional<DashboardCard> dashboardCardOptional = dashboardCardRepo.findByCompanyId(payrollReportSummary.getCompanyId());
        dashboardCard = dashboardCardOptional.orElseGet(() -> saveFreshDashboardCard(payrollReportSummary.getCompanyId()));
        long currentCount = dashboardCard.getTotalRegularPayroll();
        if (!isRollBack) {
            dashboardCard.setTotalRegularPayroll(++currentCount);
        } else {
            dashboardCard.setTotalRegularPayroll(Math.max(--currentCount, 0));
        }
        updateDashboardData(dashboardCard, payrollReportSummary, isRollBack);
    }

    public DashboardCardResponse retrieveDashboardCardData(String companyId){
        Optional<DashboardCard> dashboardCardOptional =  dashboardCardRepo.findByCompanyId(companyId);
        if(dashboardCardOptional.isPresent()){
            DashboardCard dashboardCard =  dashboardCardOptional.get();

            return DashboardCardResponse.builder()
                    .totalOffCyclePayroll(dashboardCard.getTotalOffCyclePayroll())
                    .totalRegularPayroll(dashboardCard.getTotalRegularPayroll())
                    .totalPayrollCost(dashboardCard.getTotalPayrollCost())
                    .totalNetPayrollCost(dashboardCard.getTotalNetPayrollCost())
                    .averageEmployeeCost(dashboardCard.getAverageEmployeeCost())
                    .lastUpdatedAt(dashboardCard.getLastUpdatedAt().toString())
                    .build();
        }
        return DashboardCardResponse.builder()
                .build();
    }

    public Map<String, Object> getDashboardGraph(PaymentFrequencyEnum paymentFrequencyEnum, String companyId, int page, int size) {
        Pageable paging = PageRequest.of(page, size);
//        Page<DashboardGraph> dashboardGraphs = dashboardGraphRepo.findDashboardGraphByPaymentFrequencyAndCompanyIdOrderByDateAddedDesc(paymentFrequencyEnum, companyId, paging);
        Page<DashboardGraph> dashboardGraphs2 = dashboardGraphRepo.findDashboardGraphByCompanyIdOrderByDateAddedDesc(companyId, paging);
        List<DashboardGraph> dashboardGraphList = dashboardGraphs2.getContent();
        List<DashboardGraphResponse> dashboardResponse = ReportUtils.transformToResponse(dashboardGraphList);


        Map<String, Object> response = new HashMap<>();
        response.put("payrollDetails", dashboardResponse);
        response.put("currentPage", dashboardGraphs2.getNumber());
        response.put("totalItems", dashboardGraphs2.getTotalElements());
        response.put("totalPages", dashboardGraphs2.getTotalPages());
        return response;
    }

    public void updateYTDReport(String id, String companyId, boolean isRollBack) {
        List<PayrollReportDetail>  payrollReportDetailList = payrollReportDetailRepo.findPayrollReportDetailBySummaryId(id);
        payrollAsyncService.offLoadNewValuesToYTD(payrollReportDetailList, companyId, isRollBack);
    }

    public void rollbackDashboardGraph(String companyId, String startDate, String endDate) {
        Optional<DashboardGraph> dashboardGraphOptional = dashboardGraphRepo.findByCompanyIdAndStartDateAndEndDate(companyId, startDate, endDate);
        dashboardGraphOptional.ifPresent(dashboardGraphRepo::delete);
    }

    private void updateDashboardData(DashboardCard dashboardCard, PayrollReportSummary payrollReportSummary, boolean isRollBack) {
        BigDecimal netPay = extractNetPayFromReport(payrollReportSummary);
        BigDecimal grossPay = extractGrossPayFromReport(payrollReportSummary);
        BigDecimal currentGrossPay = dashboardCard.getTotalPayrollCost();
        BigDecimal currentNetPay = dashboardCard.getTotalNetPayrollCost();


        if (!isRollBack) {
            dashboardCard.setTotalPayrollCost(currentGrossPay.add(grossPay));
            dashboardCard.setTotalNetPayrollCost(currentNetPay.add(netPay));

            dashboardCard.setAverageEmployeeCost(ComputationUtils.roundToTwoDecimalPlaces(
                    currentGrossPay.add(grossPay)
                            .divide(BigDecimal.valueOf(payrollReportSummary.getTotalNumberOfEmployees()), 2, RoundingMode.HALF_UP)
            ));
        } else {
            dashboardCard.setTotalPayrollCost(
                    currentGrossPay.subtract(grossPay).max(BigDecimal.ZERO)
            );

            dashboardCard.setTotalNetPayrollCost(
                    currentNetPay.subtract(netPay).max(BigDecimal.ZERO)
            );

            dashboardCard.setAverageEmployeeCost(
                    ComputationUtils.roundToTwoDecimalPlaces(
                            currentGrossPay.subtract(grossPay)
                                    .divide(
                                            BigDecimal.valueOf(payrollReportSummary.getTotalNumberOfEmployees()),
                                            2,
                                            RoundingMode.HALF_UP
                                    )
                                    .max(BigDecimal.ZERO)
                    )
            );
        }

        dashboardCardRepo.save(dashboardCard);

        String companyId = payrollReportSummary.getCompanyId();
        String startDate = payrollReportSummary.getStartDate();
        String endDate = payrollReportSummary.getEndDate();

        if(isRollBack) {
            dashboardGraphRepo.deleteByCompanyIdAndStartDateAndEndDate(companyId, startDate, endDate);
        } else {
            Optional<DashboardGraph> dashboardGraphOptional = dashboardGraphRepo.findByCompanyIdAndStartDateAndEndDate(companyId, startDate, endDate);
            DashboardGraph dashboardGraph;
            if (dashboardGraphOptional.isPresent()) {
                dashboardGraph = dashboardGraphOptional.get();
                dashboardGraph.setNetPay(grossPay);
                dashboardGraph.setPaymentFrequency(payrollReportSummary.getPaymentFrequency());
                dashboardGraph.setDateAdded(LocalDateTime.now());
            } else {
                dashboardGraph = DashboardGraph.builder()
                        .id(UUID.randomUUID().toString())
                        .companyId(companyId)
                        .startDate(startDate)
                        .endDate(endDate)
                        .paymentFrequency(payrollReportSummary.getPaymentFrequency())
                        .netPay(grossPay)
                        .dateAdded(LocalDateTime.now())
                        .build();
            }
            dashboardGraphRepo.save(dashboardGraph);
        }
        updateYTDReport(payrollReportSummary.getId().toString(), payrollReportSummary.getCompanyId(), isRollBack );
    }

    private BigDecimal extractNetPayFromReport(PayrollReportSummary payrollReportSummary){
        ReportResponse reportResponse = ReportUtils.transform(payrollReportSummary);
        return reportResponse.getSummary().getSummary().get(MapKeys.TOTAL_NET_PAY);
    }

    private BigDecimal extractGrossPayFromReport(PayrollReportSummary payrollReportSummary){
        ReportResponse reportResponse = ReportUtils.transform(payrollReportSummary);
        return reportResponse.getSummary().getSummary().get(MapKeys.TOTAL_GROSS_PAY);
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

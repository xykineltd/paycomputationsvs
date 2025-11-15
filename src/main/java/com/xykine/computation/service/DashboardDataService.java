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
import org.xykine.payroll.model.PaymentSettingsResponse;


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
        Optional<DashboardCard> dashboardCardOptional = dashboardCardRepo.findByCompanyId(payrollReportSummary.getCompanyId());
        DashboardCard dashboardCard = dashboardCardOptional.orElseGet(() -> saveFreshDashboardCard(payrollReportSummary.getCompanyId()));

        long currentCount = dashboardCard.getTotalOffCyclePayroll();
        dashboardCard.setTotalOffCyclePayroll(++currentCount);
        updateDashboardData(dashboardCard, payrollReportSummary, isRollback);
    }

    public void updatePayrollCountTypeRegular(PayrollReportSummary payrollReportSummary, boolean isRollBack) {
        Optional<DashboardCard> dashboardCardOptional = dashboardCardRepo.findByCompanyId(payrollReportSummary.getCompanyId());
        DashboardCard dashboardCard = dashboardCardOptional.orElseGet(() -> saveFreshDashboardCard(payrollReportSummary.getCompanyId()));
        long currentCount = dashboardCard.getTotalRegularPayroll();
        if (!isRollBack) {
            dashboardCard.setTotalRegularPayroll(++currentCount);
        } else {
            dashboardCard.setTotalRegularPayroll(--currentCount);
        }
        updateDashboardData(dashboardCard, payrollReportSummary, isRollBack);
    }

    public DashboardCardResponse retrieveDashboardCardData(String companyId){
        //TODO update the logic to use optionla before get()
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

    public void updateYTDReport(String id, String companyId, boolean isRollBack) {
        List<PayrollReportDetail>  payrollReportDetailList = payrollReportDetailRepo.findPayrollReportDetailBySummaryId(id);
        payrollAsyncService.offLoadNewValuesToYTD(payrollReportDetailList, companyId, isRollBack);
    }

    private void updateDashboardData(DashboardCard dashboardCard, PayrollReportSummary payrollReportSummary, boolean isRollBack) {
        BigDecimal netPay = extractNetPayFromReport(payrollReportSummary);
        BigDecimal currentNetPay = dashboardCard.getTotalPayrollCost();
        if (!isRollBack) {
            dashboardCard.setTotalPayrollCost(currentNetPay.add(netPay));
            dashboardCard.setAverageEmployeeCost(ComputationUtils.roundToTwoDecimalPlaces(
                    currentNetPay.add(netPay)
                            .divide(BigDecimal.valueOf(payrollReportSummary.getTotalNumberOfEmployees()), 2, RoundingMode.HALF_UP)
            ));
        } else {
            dashboardCard.setTotalPayrollCost(currentNetPay.subtract(netPay));
            dashboardCard.setAverageEmployeeCost(ComputationUtils.roundToTwoDecimalPlaces(
                    currentNetPay.subtract(netPay)
                            .divide(BigDecimal.valueOf(payrollReportSummary.getTotalNumberOfEmployees()), 2, RoundingMode.HALF_UP)
            ));
        }

        LOGGER.debug(" ====> netPay, currentNetPay,  payrollReportSummary.getTotalNumberOfEmployees()  {} {} {} ",
                netPay, currentNetPay, payrollReportSummary.getTotalNumberOfEmployees());

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
        updateYTDReport(payrollReportSummary.getId().toString(), payrollReportSummary.getCompanyId(), isRollBack );
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
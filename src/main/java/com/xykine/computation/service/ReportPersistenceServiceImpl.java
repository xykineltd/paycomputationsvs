package com.xykine.computation.service;

import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.entity.simulate.PayrollReportDetailSimulate;
import com.xykine.computation.entity.simulate.PayrollReportSummarySimulate;
import com.xykine.computation.exceptions.PayrollUnmodifiableException;
import com.xykine.computation.model.MapKeys;
import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.repo.PayrollReportDetailRepo;
import com.xykine.computation.repo.PayrollReportSummaryRepo;
import com.xykine.computation.repo.simulate.PayrollReportDetailSimulateRepo;
import com.xykine.computation.repo.simulate.PayrollReportSummarySimulateRepo;
import com.xykine.computation.request.UpdateReportRequest;
import com.xykine.computation.response.*;
import com.xykine.computation.utils.ReportUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportPersistenceServiceImpl implements ReportPersistenceService {

    private final PayrollReportSummaryRepo payrollReportSummaryRepo;
    private final PayrollReportSummarySimulateRepo payrollReportSummaryRepoSimulate;
    private final PayrollReportDetailRepo payrollReportDetailRepo;
    private final PayrollReportDetailSimulateRepo payrollReportDetailRepoSimulate;
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCalculatorImpl.class);

    @Transactional
    public ReportResponse serializeAndSaveReport(PaymentComputeResponse paymentComputeResponse, String companyId)
            throws IOException {
        long startTime = System.currentTimeMillis();
        ReportResponse reportResponse = null;
        try {
            if(paymentComputeResponse.isPayrollSimulation()) {
                //delete and replace
                payrollReportDetailRepoSimulate.deleteAll();
                payrollReportSummaryRepoSimulate.deleteAll();
                LOGGER.info("Simulated report with start date: " + paymentComputeResponse.getStart() + " will be saved.");
                reportResponse = getReportResponseSimulate(paymentComputeResponse, companyId, paymentComputeResponse.getStart());
            } else {
                //delete and replace based on pay period, ie only 1 pay period in the database and companyID
                deleteReportByDate(
                        paymentComputeResponse.getStart(),
                        companyId,
                        paymentComputeResponse.isOffCycle(),
                        false,
                        paymentComputeResponse.getOffCycleId()
                        );
                reportResponse = getReportResponse(paymentComputeResponse, companyId, paymentComputeResponse.getStart());
            }
        } catch (RuntimeException e) {
            LOGGER.info(" exception {} ", e.toString());
            throw e;
        }
        long endTime = System.currentTimeMillis();
        LOGGER.info(" Process time ===> {} ms", endTime - startTime );
        return reportResponse;
    }
    private ReportResponse getReportResponse(PaymentComputeResponse paymentComputeResponse, String companyId, String startDate) {
        PayComputeSummaryResponse payComputeSummaryResponse = PayComputeSummaryResponse.builder()
                .summary(paymentComputeResponse.getSummary())
                .build();
        PayrollReportSummary payrollReportSummary = PayrollReportSummary.builder()
                .id(paymentComputeResponse.getId())
                .companyId(companyId)
                .offCycleId(paymentComputeResponse.getOffCycleId())
                .startDate(LocalDate.parse(startDate))
                .endDate(LocalDate.parse(paymentComputeResponse.getEnd()))
                .report(ReportUtils.serializeResponse(payComputeSummaryResponse))
                .createdDate(LocalDateTime.now())
                .payrollSimulation(paymentComputeResponse.isPayrollSimulation())
                .offCycle(paymentComputeResponse.isOffCycle())
                .build();
        payrollReportSummaryRepo.save(payrollReportSummary);
        saveReportDetails(paymentComputeResponse, companyId, payrollReportSummary.isPayrollApproved());
        return getPayRollReport(paymentComputeResponse.getId());
    }
    public ReportResponse getPayRollReport(UUID id){
        PayrollReportSummary payrollReportSummary = payrollReportSummaryRepo.findPayrollReportSummaryById(id);
        if(payrollReportSummary == null){
            throw new RuntimeException("Report with id: " + id + " was not found");
        }
        return ReportUtils.transform(payrollReportSummary);
    }
    private ReportResponse getReportResponseSimulate(PaymentComputeResponse paymentComputeResponse, String companyId, String startDate) {
        PayComputeSummaryResponse payComputeSummaryResponse = PayComputeSummaryResponse.builder()
                .summary(paymentComputeResponse.getSummary())
                .build();
        PayrollReportSummarySimulate payrollReportSummary = PayrollReportSummarySimulate.builder()
                .id(paymentComputeResponse.getId())
                .companyId(companyId)
                .startDate(LocalDate.parse(startDate))
                .endDate(LocalDate.parse(paymentComputeResponse.getEnd()))
                .report(ReportUtils.serializeResponse(payComputeSummaryResponse))
                .createdDate(LocalDateTime.now())
                .payrollSimulation(paymentComputeResponse.isPayrollSimulation())
                .build();
        payrollReportSummaryRepoSimulate.save(payrollReportSummary);
        saveReportDetailsSimulate(paymentComputeResponse, companyId);
        return getPayRollReportSimulate(paymentComputeResponse.getStart());
    }

    @Override
    public ReportResponse getPayRollReport(String starDate, String companyId){
       PayrollReportSummary payrollReportSummary = payrollReportSummaryRepo
                .findPayrollReportSummaryByStartDateAndCompanyIdAndPayrollSimulation(LocalDate.parse(starDate), companyId, false);
       if(payrollReportSummary == null){
           return null;
       }
        return ReportUtils.transform(payrollReportSummary);
    }


    private List<ReportResponse> getPayRollReportOffCycle(String companyId){
        return payrollReportSummaryRepo
                .findAllByCompanyIdAndPayrollSimulationAndOffCycle(companyId, false, true)
                .stream()
                .map(ReportUtils::transform)
                .collect(Collectors.toList());
    }

    public ReportResponse getPayRollReportSimulate(String starDate){
        PayrollReportSummarySimulate payrollReportSummary = payrollReportSummaryRepoSimulate.findPayrollReportSummaryByStartDate(LocalDate.parse(starDate));

        if(payrollReportSummary == null){
            //return empty
            return new ReportResponse();
        }
        return ReportUtils.transform(payrollReportSummary);
    }

    //Pull both all report summary for display on dashboard
    public List<ReportResponse> getPayRollReports(String companyId){
        List<ReportResponse> summary = getPayRollReportSimulates(companyId);
        var reports = payrollReportSummaryRepo.findAllByCompanyIdOrderByCreatedDateAsc(companyId).stream()
                .map(ReportUtils::transform).toList();
        summary.addAll(reports);
        return summary;
    }
    private List<ReportResponse> getPayRollReportSimulates(String companyId){
        return  payrollReportSummaryRepoSimulate.findAllByCompanyIdOrderByCreatedDateAsc(companyId).stream()
                .map(ReportUtils::transform)
                .collect(Collectors.toList());
    }

    @Transactional
    public PayrollReportSummary approveReport(UpdateReportRequest request) {
        PayrollReportSummary existingSummaryReport;
        if(request.isOffCycle()) {
            existingSummaryReport = payrollReportSummaryRepo
                    .findPayrollReportSummaryByCompanyIdAndOffCycleId(request.getCompanyId(), request.getOffCycleId());
        } else {
            existingSummaryReport = payrollReportSummaryRepo
                    .findPayrollReportSummaryByStartDateAndCompanyIdAndPayrollSimulation(LocalDate.parse(request.getStartDate()), request.getCompanyId(), false);
        }
        existingSummaryReport.setPayrollApproved(request.isPayrollApproved());
        payrollReportSummaryRepo.save(existingSummaryReport);
        //TODO update the detail report once the payroll is approved
        return  existingSummaryReport;
    }
    public boolean deleteReport(UpdateReportRequest request) {
        return deleteReportByDate(request.getStartDate(),
                request.getCompanyId(),
                request.isOffCycle(),
                request.isCancelPayroll(),
                request.getOffCycleId()
                );
    }

    private boolean deleteReportByDate(String startDate,
                                       String companyId,
                                       boolean isOffCycle,
                                       boolean isCancelPayroll,
                                       String offCycleId
    ) {
        if(isOffCycle && !isCancelPayroll) return false;

        //canceling offCycle payroll
        if(isOffCycle) {
            payrollReportSummaryRepo.deletePayrollReportSummaryByOffCycleIdAndCompanyId(offCycleId, companyId);
            payrollReportDetailRepo.deleteAllByOffCycleIdAndCompanyId(offCycleId, companyId);
            return true;
        }

        var payroll = payrollReportSummaryRepo
                .findPayrollReportSummaryByPayrollApprovedAndStartDateAndCompanyId(true, LocalDate.parse(startDate), companyId);
        if(payroll != null && payroll.isPayrollApproved()) {
            throw new PayrollUnmodifiableException(startDate);
        }

        //canceling regular payroll
        payrollReportSummaryRepo.deletePayrollReportSummaryByStartDateAndCompanyId(LocalDate.parse(startDate), companyId);
        payrollReportDetailRepo.deleteAllByStartDateAndCompanyId(LocalDate.parse(startDate), companyId);
        return true;
    }

    @Override
    public Map<String, Object> getPaymentDetails(String id, String companyId, int page, int size) {
        List<PayrollReportDetail> payrollDetails = new ArrayList<>();
        Pageable paging = PageRequest.of(page, size);
        Page<PayrollReportDetail> payrollReportDetailPage = payrollReportDetailRepo.findPayrollReportDetailBySummaryIdAndCompanyId(id, companyId, paging);

        // if report detail is empty then check the simulated report detail table. No need for different endpoint.
        //TODO what if the payrollReportDetailPage above is not empty and we need to get the report for simulated payroll
        if (payrollReportDetailPage.isEmpty()) {
            payrollReportDetailPage = payrollReportDetailRepoSimulate.findPayrollReportDetailBySummaryIdAndCompanyId(id, companyId, paging);
        }

        payrollDetails = payrollReportDetailPage.getContent();
        List<ReportResponse> reportResponses = ReportUtils.transform(payrollDetails);

        Map<String, Object> response = new HashMap<>();
        response.put("payrollDetails", reportResponses);
        response.put("currentPage", payrollReportDetailPage.getNumber());
        response.put("totalItems", payrollReportDetailPage.getTotalElements());
        response.put("totalPages", payrollReportDetailPage.getTotalPages());
        return response;
    }

    @Override
    public List<ReportAnalytics> getReportAnalytics(String companyId) {

        var regularPayrolls =  generateDateFromJanToDecember().stream().map(
                date -> getReportAnalytics(getPayRollReport(date.toString(), companyId), companyId)
        ).filter( r -> r.getReportId() != null)
                .toList();

        // get for the offCyclePayrolls
        var offCyclePayrolls =  getPayRollReportOffCycle(companyId).stream().map(
                        reportResponse -> getReportAnalytics(reportResponse, companyId)
                ).filter( r -> r.getReportId() != null)
                .toList();


        List<ReportAnalytics> mergedList = new ArrayList<>(regularPayrolls);

        mergedList.addAll(offCyclePayrolls);
        return mergedList;
    }
    private List<LocalDate> generateDateFromJanToDecember() {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();

        for (int month = 1; month <= 12; month++) {
            // Create a LocalDate object for the first day of each month in the current year
            LocalDate firstDayOfMonth = LocalDate.of(currentYear, month, 1);
            dates.add(firstDayOfMonth);
        }
        return dates;
    }
    private ReportAnalytics getReportAnalytics(ReportResponse reportSummary, String companyId) {
        try {
            if(reportSummary == null) return new ReportAnalytics();

            int veryHighLimit = Integer.MAX_VALUE;
            Pageable pageable = PageRequest.of(0, veryHighLimit);
            var reportDetails = payrollReportDetailRepo.findPayrollReportDetailBySummaryIdAndCompanyId(reportSummary.getReportId(), companyId, pageable);

            var numberOfPays = reportDetails.getTotalElements();
            var employeeCount = getDistinctEmployeesCount(reportDetails);

            var reportAnalytics = new ReportAnalytics(
                    reportSummary.getStartDate(),
                    employeeCount,
                    numberOfPays,
                    reportSummary.getSummary().getSummary().get(MapKeys.TOTAL_NET_PAY),
                    reportSummary.isPayrollApproved() ? "Completed" : "Pending",
                    reportSummary.getReportId(),
                    reportSummary.getCompanyId()
            );
            return reportAnalytics;
        } catch (RuntimeException ex) {
            LOGGER.info(ex.getMessage());
            return new ReportAnalytics();
        }
    }
    private static long getDistinctEmployeesCount(Page<PayrollReportDetail> reportDetails) {
        Set<String> distinctEmployeeIds = reportDetails.stream()
                .map(PayrollReportDetail::getEmployeeId)
                .collect(Collectors.toSet());
        return distinctEmployeeIds.size();
    }
    private void saveReportDetails(PaymentComputeResponse paymentComputeResponse, String companyId, boolean isPayrollApproved) {
        List<PaymentInfo> paymentInfoList = paymentComputeResponse.getReport();
        CompletableFuture<Void> jobFuture = CompletableFuture.supplyAsync(() -> {
            paymentInfoList.forEach(x -> {
                PayComputeDetailResponse payComputeDetailResponse = PayComputeDetailResponse.builder()
                        .report(x)
                        .build();
                PayrollReportDetail payrollReportDetail = PayrollReportDetail.builder()
                        .id(UUID.randomUUID().toString())
                        .employeeId(x.getEmployeeID())
                        .summaryId(paymentComputeResponse.getId().toString())
                        .companyId(companyId)
                        .offCycleId(paymentComputeResponse.getOffCycleId())
                        .departmentId(x.getDepartmentID())
                        .startDate(paymentComputeResponse.getStart())
                        .endDate((paymentComputeResponse.getEnd()))
                        .report(ReportUtils.serializeResponse(payComputeDetailResponse))
                        .createdDate(LocalDateTime.now())
                        .payrollSimulation(paymentComputeResponse.isPayrollSimulation())
                        .payrollApproved(isPayrollApproved)
                        .offCycle(paymentComputeResponse.isOffCycle())
                        .build();
                payrollReportDetailRepo.save(payrollReportDetail);
                //LOGGER.info("saving in repo ==> {}", payrollReportDetail);
            });
            return null;
        });
        try {
            jobFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
    private void saveReportDetailsSimulate(PaymentComputeResponse paymentComputeResponse, String companyId) {
        List<PaymentInfo> paymentInfoList = paymentComputeResponse.getReport();
        CompletableFuture<Void> jobFuture = CompletableFuture.supplyAsync(() -> {
            paymentInfoList.forEach(x -> {
                PayComputeDetailResponse payComputeDetailResponse = PayComputeDetailResponse.builder()
                        .report(x)
                        .build();
                PayrollReportDetailSimulate payrollReportDetail = PayrollReportDetailSimulate.builder()
                        .id(UUID.randomUUID().toString())
                        .employeeId(x.getEmployeeID())
                        .summaryId(paymentComputeResponse.getId().toString())
                        .companyId(companyId.toString())
                        .departmentId(x.getDepartmentID())
                        .startDate(paymentComputeResponse.getStart())
                        .endDate((paymentComputeResponse.getEnd()))
                        .report(ReportUtils.serializeResponse(payComputeDetailResponse))
                        .createdDate(LocalDateTime.now())
                        .payrollSimulation(paymentComputeResponse.isPayrollSimulation())
                        .build();

                payrollReportDetailRepoSimulate.save(payrollReportDetail);
            });
            return null;
        });
        try {
            jobFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}

package com.xykine.computation.service;

import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.entity.simulate.PayrollReportDetailSimulate;
import com.xykine.computation.entity.simulate.PayrollReportSummarySimulate;
import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.repo.PayrollReportDetailRepo;
import com.xykine.computation.repo.PayrollReportSummaryRepo;
import com.xykine.computation.repo.simulate.PayrollReportDetailSimulateRepo;
import com.xykine.computation.repo.simulate.PayrollReportSummarySimulateRepo;
import com.xykine.computation.request.UpdateReportRequest;
import com.xykine.computation.response.PayComputeDetailResponse;
import com.xykine.computation.response.PayComputeSummaryResponse;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.response.ReportResponse;
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
        ReportResponse reportResponse = null;
        try {
            if(paymentComputeResponse.isPayrollSimulation()) {
                //delete and replace
                payrollReportDetailRepoSimulate.deleteAll();
                payrollReportSummaryRepoSimulate.deleteAll();
                LOGGER.info("Simulated report with start date: " + paymentComputeResponse.getStart() + " will be saved.");
                reportResponse = getReportResponseSimulate(paymentComputeResponse, companyId, paymentComputeResponse.getStart());
            } else {
                reportResponse = getReportResponse(paymentComputeResponse, companyId, paymentComputeResponse.getStart());
            }
        } catch (RuntimeException e) {
            var startDate = paymentComputeResponse.getStart();
            LOGGER.info("Report with the start date " + startDate + " does not exist. A new report will be saved.");
        }
        return reportResponse;
    }

    private ReportResponse getReportResponse(PaymentComputeResponse paymentComputeResponse, String companyId, String startDate) {
        PayComputeSummaryResponse payComputeSummaryResponse = PayComputeSummaryResponse.builder()
                .summary(paymentComputeResponse.getSummary())
                .build();
        PayrollReportSummary payrollReportSummary = PayrollReportSummary.builder()
                .id(paymentComputeResponse.getId())
                .companyId(companyId)
                .startDate(LocalDate.parse(startDate))
                .endDate(LocalDate.parse(paymentComputeResponse.getEnd()))
                .report(ReportUtils.serializeResponse(payComputeSummaryResponse))
                .createdDate(LocalDateTime.now())
                .payrollSimulation(paymentComputeResponse.isPayrollSimulation())
                .build();
        payrollReportSummaryRepo.save(payrollReportSummary);
        saveReportDetails(paymentComputeResponse, companyId);
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

    public ReportResponse getPayRollReport(String starDate){
       PayrollReportSummary payrollReportSummary = payrollReportSummaryRepo.findPayrollReportSummaryByStartDateAndPayrollSimulation(LocalDate.parse(starDate), false);
       if(payrollReportSummary == null){
           throw new RuntimeException("Report with the date: " + starDate + " was not found");
       }
       return ReportUtils.transform(payrollReportSummary);
    }

    public ReportResponse getPayRollReportSimulate(String starDate){
        PayrollReportSummarySimulate payrollReportSummary = payrollReportSummaryRepoSimulate.findPayrollReportSummaryByStartDate(LocalDate.parse(starDate));

        if(payrollReportSummary == null){
            //return empty
            return new ReportResponse();
        }
        return ReportUtils.transform(payrollReportSummary);
    }

    public List<ReportResponse> getPayRollReports(){
        List<ReportResponse> summary = getPayRollReportSimulates();
        var reports = payrollReportSummaryRepo.findAllByOrderByCreatedDateAsc().stream()
                .map(ReportUtils::transform).toList();
        summary.addAll(reports);
        return summary;
    }

    private List<ReportResponse> getPayRollReportSimulates(){
        return  payrollReportSummaryRepoSimulate.findAllByOrderByCreatedDateAsc().stream()
                .map(ReportUtils::transform)
                .collect(Collectors.toList());
    }
    @Transactional
    public PayrollReportSummary approveReport(UpdateReportRequest request) {
        var existingSummaryReport = payrollReportSummaryRepo.findPayrollReportSummaryByStartDateAndPayrollSimulation(LocalDate.parse(request.getStartDate()), false);
        existingSummaryReport.setPayrollApproved(request.isPayrollApproved());
        payrollReportSummaryRepo.save(existingSummaryReport);
        return  existingSummaryReport;
    }

    public boolean deleteReport(UpdateReportRequest request) {
            payrollReportSummaryRepo.deletePayrollReportSummaryByStartDate(LocalDate.parse(request.getStartDate()));
            payrollReportDetailRepo.deletePayrollReportDetailByStartDate(LocalDate.parse(request.getStartDate()));
            return true;
    }

    @Override
    public Map<String, Object> getPaymentDetails(String id, int page, int size) {
        List<PayrollReportDetail> payrollDetails = new ArrayList<>();
        Pageable paging = PageRequest.of(page, size);
        Page<PayrollReportDetail> payrollReportDetailPage = payrollReportDetailRepo.findPayrollReportDetailBySummaryId(id, paging);

        // if report detail is empty then check the simulated report detail table. No need for different endpoint.
        if (payrollReportDetailPage.isEmpty()) {
            payrollReportDetailPage = payrollReportDetailRepoSimulate.findPayrollReportDetailBySummaryId(id, paging);
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

    private void saveReportDetails(PaymentComputeResponse paymentComputeResponse, String companyId) {
        List<PaymentInfo> paymentInfoList = paymentComputeResponse.getReport();
        CompletableFuture<Void> jobFuture = CompletableFuture.supplyAsync(() -> {
            paymentInfoList.stream().forEach(x -> {
                PayComputeDetailResponse payComputeDetailResponse = PayComputeDetailResponse.builder()
                        .report(x)
                        .build();
                PayrollReportDetail payrollReportDetail = PayrollReportDetail.builder()
                        .id(UUID.randomUUID().toString())
                        .summaryId(paymentComputeResponse.getId().toString())
                        .companyId(companyId.toString())
                        .departmentId(x.getDepartmentID())
                        .startDate(paymentComputeResponse.getStart())
                        .endDate((paymentComputeResponse.getEnd()))
                        .report(ReportUtils.serializeResponse(payComputeDetailResponse))
                        .createdDate(LocalDateTime.now())
                        .payrollSimulation(paymentComputeResponse.isPayrollSimulation())
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

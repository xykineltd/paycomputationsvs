package com.xykine.computation.service;

import com.xykine.computation.entity.PayrollReport;
import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.repo.PayrollReportDetailRepo;
import com.xykine.computation.repo.PayrollReportRepo;
import com.xykine.computation.repo.PayrollReportSummaryRepo;
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
    private final PayrollReportDetailRepo payrollReportDetailRepo;
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCalculatorImpl.class);


    @Transactional
    public ReportResponse serializeAndSaveReport(PaymentComputeResponse paymentComputeResponse, Long companyId)
            throws IOException, ClassNotFoundException {
        //if isSimulated remove old one and save new one, we don't want many simulated reports
        if(paymentComputeResponse.isPayrollSimulation()) {
            payrollReportDetailRepo.deletePayrollReportsByPayrollSimulation(true);
            payrollReportSummaryRepo.deletePPayrollReportSummaryByPayrollSimulation(true);
        }

        LOGGER.info(" report id ==> {}", paymentComputeResponse.getId());

        PayComputeSummaryResponse payComputeSummaryResponse = PayComputeSummaryResponse.builder()
                .summary(paymentComputeResponse.getSummary())
                .build();
        PayrollReportSummary payrollReportSummary = PayrollReportSummary.builder()
                .id(paymentComputeResponse.getId())
                .companyId(companyId.toString())
                .startDate(LocalDate.parse(paymentComputeResponse.getStart()))
                .endDate(LocalDate.parse(paymentComputeResponse.getEnd()))
                .report(ReportUtils.serializeResponse(payComputeSummaryResponse))
                .createdDate(LocalDateTime.now())
                .build();
        payrollReportSummaryRepo.save(payrollReportSummary);
        saveReportDetails(paymentComputeResponse, companyId);
        return getPayRollReport(paymentComputeResponse.getStart());
    }

    public ReportResponse getPayRollReport(String starDate){
       PayrollReportSummary payrollReportSummary = payrollReportSummaryRepo.findPayrollReportSummaryByStartDateAndPayrollSimulation(LocalDate.parse(starDate), false);
       return ReportUtils.transform(payrollReportSummary);
    }

    public List<ReportResponse> getPayRollReports(){
            return  payrollReportSummaryRepo.findAllByOrderByCreatedDateAsc().stream()
                    .map(r -> ReportUtils.transform(r))
                    .collect(Collectors.toList());
    }
    @Transactional
    public PayrollReportSummary updateReport(UpdateReportRequest request) {
        var existingReport = payrollReportSummaryRepo.findPayrollReportSummaryByStartDateAndPayrollSimulation(LocalDate.parse(request.getStartDate()), false);
        existingReport.setPayrollApproved(request.isPayrollApproved());
        return payrollReportSummaryRepo.save(existingReport);
    }

    @Override
    public Map<String, Object> getPaymentDetails(String id, int page, int size) {
        List<PayrollReportDetail> payrollDetails = new ArrayList<>();
        Pageable paging = PageRequest.of(page, size);
        Page<PayrollReportDetail> payrollReportDetailPage = payrollReportDetailRepo.findPayrollReportDetailBySummaryId(id, paging);
        payrollDetails = payrollReportDetailPage.getContent();

        List<ReportResponse> reportResponses = ReportUtils.transform(payrollDetails);

        Map<String, Object> response = new HashMap<>();
        response.put("payrollDetails", reportResponses);
        response.put("currentPage", payrollReportDetailPage.getNumber());
        response.put("totalItems", payrollReportDetailPage.getTotalElements());
        response.put("totalPages", payrollReportDetailPage.getTotalPages());
        return response;
    }

    private void saveReportDetails(PaymentComputeResponse paymentComputeResponse, Long companyId) {
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
                        .departmentId(x.getDepartmentId())
                        .startDate(paymentComputeResponse.getStart())
                        .endDate((paymentComputeResponse.getEnd()))
                        .report(ReportUtils.serializeResponse(payComputeDetailResponse))
                        .createdDate(LocalDateTime.now())
                        .build();
                payrollReportDetailRepo.save(payrollReportDetail);
                //LOGGER.info("saving in repo ==> {}", payrollReportDetail);
            });
            return null;
        });
        try {
            jobFuture.get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}

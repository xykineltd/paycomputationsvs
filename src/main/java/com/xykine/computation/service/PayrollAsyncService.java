package com.xykine.computation.service;


import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.entity.PayrollStatus;

import com.xykine.computation.repo.PayrollReportDetailRepo;

import com.xykine.computation.request.RepaymentRequest;
import com.xykine.computation.response.PayComputeDetailResponse;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.utils.ReportUtils;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import org.xykine.payroll.model.PaymentInfo;
import org.xykine.payroll.model.enums.PaymentTypeEnum;


import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.xykine.computation.service.ReportPersistenceServiceImpl.mergeMaps;

@Service
@RequiredArgsConstructor
public class PayrollAsyncService {

    private final PayrollReportDetailRepo payrollReportDetailRepo;
    private final LoanService loanService;

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
}

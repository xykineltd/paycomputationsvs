package com.xykine.computation.service;

import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.entity.PayrollStatus;
import com.xykine.computation.repo.PayrollReportDetailRepo;
import com.xykine.computation.request.RepaymentRequest;
import com.xykine.computation.utils.ReportUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.xykine.payroll.model.enums.PaymentTypeEnum;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PayrollAsyncService {

    private final PayrollReportDetailRepo payrollReportDetailRepo;
    private final LoanService loanService;

    @Async
    public void updateDetailStatusAsync(String summaryId) {
        List<PayrollReportDetail> details = payrollReportDetailRepo.findPayrollReportDetailBySummaryId(summaryId);
        details.forEach(d -> {
            d.setPayrollStatus(PayrollStatus.APPROVED);
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
}

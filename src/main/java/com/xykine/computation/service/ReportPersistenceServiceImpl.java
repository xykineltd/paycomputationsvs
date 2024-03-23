package com.xykine.computation.service;

import com.xykine.computation.entity.PayrollReport;
import com.xykine.computation.repo.PayrollReportRepo;
import com.xykine.computation.request.UpdateReportRequest;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.utils.ReportUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportPersistenceServiceImpl implements ReportPersistenceService {

    private final PayrollReportRepo payrollReportRepo;
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCalculatorImpl.class);


    @Transactional
    public void serializeAndSaveReport(PaymentComputeResponse paymentComputeResponse)
            throws IOException, ClassNotFoundException {
        //if isSimulated remove old one and save new one, we don't want many simulated reports
        if(paymentComputeResponse.isPayrollSimulation()) {
            payrollReportRepo.deletePayrollReportsByPayrollSimulation(true);
        }

        PayrollReport payrollReport = PayrollReport.builder()
                .startDate(LocalDate.parse(paymentComputeResponse.getStart()))
                .endDate(LocalDate.parse(paymentComputeResponse.getEnd()))
                .createdDate(LocalDateTime.now())
                .report(ReportUtils.serializeResponse(paymentComputeResponse))
                .build();
        payrollReportRepo.save(payrollReport);
    }

    public ReportResponse getPayRollReport(String starDate){
       PayrollReport payrollReport = payrollReportRepo.findPayrollReportByStartDateAndPayrollSimulation(LocalDate.parse(starDate), false);
       return ReportUtils.transform(payrollReport);
    }

    public List<ReportResponse> getPayRollReports(){
            return  payrollReportRepo.findAllByOrderByCreatedDateAsc().stream()
                    .map(r -> ReportUtils.transform(r))
                    .collect(Collectors.toList());
    }
    @Transactional
    public PayrollReport updateReport(UpdateReportRequest request) {
        var existingReport = payrollReportRepo.findPayrollReportByStartDateAndPayrollSimulation(LocalDate.parse(request.getStartDate()), false);
        existingReport.setPayrollApproved(request.isPayrollApproved());
        return payrollReportRepo.save(existingReport);
    }

}

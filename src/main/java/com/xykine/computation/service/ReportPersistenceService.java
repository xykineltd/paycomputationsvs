package com.xykine.computation.service;

import com.xykine.computation.entity.PayrollReport;
import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.repo.PayrollReportRepo;
import com.xykine.computation.request.UpdateReportRequest;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.response.ReportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportPersistenceService {

    private final PayrollReportRepo payrollReportRepo;

    @Transactional
    public void serializeAndSaveReport(PaymentComputeResponse paymentComputeResponse)
            throws IOException, ClassNotFoundException {

        //if isSimulated remove old one and save new one, we don't want many simulated reports
        if(paymentComputeResponse.isPayrollSimulation()) {
            payrollReportRepo.deletePayrollReportsByPayrollSimulation(true);
        }

        byte[] data = SerializationUtils.serialize(paymentComputeResponse);
        PayrollReport payrollReport = PayrollReport.builder()
                .startDate(LocalDate.parse(paymentComputeResponse.getStart()))
                .endDate(LocalDate.parse(paymentComputeResponse.getEnd()))
                .createdDate(LocalDateTime.now())
                .payrollSimulation(paymentComputeResponse.isPayrollSimulation())
                .report(data)
                .build();
        payrollReportRepo.save(payrollReport);
    }

    public PaymentComputeResponse getPayRollReport(String starDate){
        PaymentComputeResponse paymentComputeResponse = SerializationUtils
                .deserialize(payrollReportRepo.findPayrollReportByStartDateAndPayrollSimulation(LocalDate.parse(starDate), false).getReport());
        return paymentComputeResponse;
    }

    public List<ReportResponse> getPayRollReports(){
            List<PaymentComputeResponse> responses = new ArrayList<>();
            List<PaymentComputeResponse> responses2 = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a");

        var all = payrollReportRepo.findAllByOrderByCreatedDateAsc();

            return  all.stream().map(
                    r -> {
                        PaymentComputeResponse rt = SerializationUtils
                                .deserialize(r.getReport());
                        return new ReportResponse(rt, r.isPayrollApproved(), r.getCreatedDate().format(formatter) );

                    }
            ).toList();
    }

    public boolean updateReport(UpdateReportRequest request) {
        System.out.println("request" + request);
        var existingReport = payrollReportRepo.findPayrollReportByStartDateAndPayrollSimulation(LocalDate.parse(request.getStartDate()), false);
        System.out.println("existingReport" + existingReport);

        if(existingReport == null) {
            throw new RuntimeException("report Not found");
        }

        payrollReportRepo.deleteById(existingReport.getId());

        PayrollReport payrollReport = PayrollReport.builder()
                .startDate(existingReport.getStartDate())
                .endDate(existingReport.getEndDate())
                .createdDate(LocalDateTime.now())
                .payrollSimulation(existingReport.isPayrollSimulation())
                .payrollApproved(request.isPayrollApproved())
                .report(existingReport.getReport())
                .build();
        payrollReportRepo.save(payrollReport);
         return true;
    }

}

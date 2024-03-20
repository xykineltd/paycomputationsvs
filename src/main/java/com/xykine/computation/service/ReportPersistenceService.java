package com.xykine.computation.service;

import com.xykine.computation.entity.PayrollReport;
import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.repo.PayrollReportRepo;
import com.xykine.computation.request.UpdateReportRequest;
import com.xykine.computation.response.PaymentComputeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
        byte[] data = SerializationUtils.serialize(paymentComputeResponse);
        PayrollReport payrollReport = PayrollReport.builder()
                .startDate(LocalDate.parse(paymentComputeResponse.getStart()))
                .endDate(LocalDate.parse(paymentComputeResponse.getEnd()))
                .createdDate(Instant.now())
                .payrollSimulation(paymentComputeResponse.isPayrollSimulation())
                .report(data)
                .build();
        System.out.println("isPayrollSimulation" + payrollReport.isPayrollSimulation());
        payrollReportRepo.save(payrollReport);
    }

    public PaymentComputeResponse getPayRollReport(String starDate){
        PaymentComputeResponse paymentComputeResponse = SerializationUtils
                .deserialize(payrollReportRepo.findPayrollReportByStartDate(LocalDate.parse(starDate)).getReport());
        return paymentComputeResponse;
    }

    public List<PaymentComputeResponse> getPayRollReports(){
            List<PaymentComputeResponse> responses = new ArrayList<>();
            var all = payrollReportRepo.findAllByOrderByCreatedDateDesc();
            all.forEach(
                    r -> {
                        System.out.println("risApproved===>" + r.isPayrollApproved());
                        System.out.println("risSimulation===>" + r.isPayrollSimulation());
                        responses.add(
                                SerializationUtils
                                        .deserialize(r.getReport()));
                    }

            );
            return responses;
    }

    public boolean updateReport(UpdateReportRequest request) {
        var existingReport = payrollReportRepo.findPayrollReportByStartDate(LocalDate.parse(request.getStartDate()));
        if(existingReport == null) {
            throw new RuntimeException("report Not found");
        }
        var reportToUpdate = new PayrollReport(
                existingReport.getId(),
                existingReport.getStartDate(),
                existingReport.getEndDate(),
                existingReport.isPayrollSimulation(),
                existingReport.getReport(),
                true,
                existingReport.getCreatedDate());

        var res = payrollReportRepo.save(reportToUpdate);
         return true;
    }

}

package com.xykine.computation.service;

import com.xykine.computation.entity.PayrollReport;
import com.xykine.computation.repo.PayrollReportRepo;
import com.xykine.computation.response.PaymentComputeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportPersistenceService {

    private final PayrollReportRepo payrollReportRepo;

    public void serializeAndSaveReport(PaymentComputeResponse paymentComputeResponse)
            throws IOException, ClassNotFoundException {
        byte[] data = SerializationUtils.serialize(paymentComputeResponse);
        PayrollReport payrollReport = PayrollReport.builder()
                .startDate(LocalDate.parse(paymentComputeResponse.getStart()))
                .endDate(LocalDate.parse(paymentComputeResponse.getEnd()))
                .report(data)
                .build();
        payrollReportRepo.save(payrollReport);
    }

    public PaymentComputeResponse getPayRollReport(String starDate){
        PaymentComputeResponse paymentComputeResponse = SerializationUtils
                .deserialize(payrollReportRepo.findPayrollReportByStartDate(LocalDate.parse(starDate)).getReport());
        return paymentComputeResponse;
    }
}

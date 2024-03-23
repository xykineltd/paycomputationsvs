package com.xykine.computation.utils;

import com.xykine.computation.entity.PayrollReport;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.response.ReportResponse;
import org.apache.commons.lang3.SerializationUtils;

public class ReportUtils {
    public static ReportResponse transform(PayrollReport payrollReport){
        PaymentComputeResponse paymentComputeResponse =  SerializationUtils.deserialize(payrollReport.getReport());
        return ReportResponse.builder()
                .payrollApproved(payrollReport.isPayrollApproved())
                .startDate(payrollReport.getStartDate().toString())
                .endDate(payrollReport.getEndDate().toString())
                .createdDate(payrollReport.getCreatedDate().toString())
                .report(paymentComputeResponse)
                .build();
    }

    public static byte[] serializeResponse(PaymentComputeResponse paymentComputeResponse) {
        return SerializationUtils.serialize(paymentComputeResponse);
    }
}

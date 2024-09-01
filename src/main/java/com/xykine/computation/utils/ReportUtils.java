package com.xykine.computation.utils;

import com.xykine.computation.entity.AuditTrail;
import com.xykine.computation.entity.DashboardGraph;
import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.entity.simulate.PayrollReportSummarySimulate;
import com.xykine.computation.response.AuditTrailResponse;
import com.xykine.computation.response.DashboardGraphResponse;
import com.xykine.computation.response.PayComputeSummaryResponse;
import com.xykine.computation.response.ReportResponse;
import org.apache.commons.lang3.SerializationUtils;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

public class ReportUtils {

    public static List<ReportResponse> transform(List<PayrollReportDetail> payrollReportDetails){
        return payrollReportDetails.stream().map(x -> ReportResponse.builder()
                .reportId(x.getId())
                .companyId(x.getCompanyId())
                .offCycleId(x.getOffCycleId())
                .departmentId(x.getDepartmentId())
                .employeeId(x.getEmployeeId())
                .fullName(x.getFullName())
                .payrollApproved(x.isPayrollApproved())
                .startDate(x.getStartDate().toString())
                .endDate(x.getEndDate().toString())
                .createdDate(String.valueOf(x.getCreatedDate()))
                .payrollApproved(x.isPayrollApproved())
                .payrollSimulated(x.isPayrollSimulation())
                .offCycle(x.isOffCycle())
                .detail(SerializationUtils.deserialize(x.getReport()))
                .build()).collect(Collectors.toList());
    }

    public static ReportResponse transform(PayrollReportDetail x){
            return ReportResponse.builder()
                    .reportId(x.getId())
                    .companyId(x.getCompanyId())
                    .offCycleId(x.getOffCycleId())
                    .departmentId(x.getDepartmentId())
                    .employeeId(x.getEmployeeId())
                    .payrollApproved(x.isPayrollApproved())
                    .startDate(x.getStartDate().toString())
                    .endDate(x.getEndDate().toString())
                    .createdDate(String.valueOf(x.getCreatedDate()))
                    .payrollApproved(x.isPayrollApproved())
                    .payrollSimulated(x.isPayrollSimulation())
                    .payrollCompleted(x.isPayrollCompleted())
                    .offCycle(x.isOffCycle())
                    .detail(SerializationUtils.deserialize(x.getReport()))
                    .build();
    }

    public static ReportResponse transform(PayrollReportSummary payrollReportSummary){
        PayComputeSummaryResponse summary =  SerializationUtils.deserialize(payrollReportSummary.getReport());
        return ReportResponse.builder()
                .reportId(payrollReportSummary.getId().toString())
                .companyId(payrollReportSummary.getCompanyId())
                .offCycleId(payrollReportSummary.getOffCycleId())
                .payrollApproved(payrollReportSummary.isPayrollApproved())
                .startDate(payrollReportSummary.getStartDate().toString())
                .endDate(payrollReportSummary.getEndDate().toString())
                .createdDate(String.valueOf(payrollReportSummary.getCreatedDate()))
                .payrollApproved(payrollReportSummary.isPayrollApproved())
                .payrollSimulated(payrollReportSummary.isPayrollSimulation())
                .payrollCompleted(payrollReportSummary.isPayrollCompleted())
                .offCycle(payrollReportSummary.isOffCycle())
                .summary(summary)
                .build();
    }

    public static ReportResponse transform(PayrollReportSummarySimulate payrollReportSummary){
        PayComputeSummaryResponse summary =  SerializationUtils.deserialize(payrollReportSummary.getReport());
        return ReportResponse.builder()
                .reportId(payrollReportSummary.getId().toString())
                .companyId(payrollReportSummary.getCompanyId())
                .payrollApproved(payrollReportSummary.isPayrollApproved())
                .startDate(payrollReportSummary.getStartDate())
                .endDate(payrollReportSummary.getEndDate())
                .createdDate(String.valueOf(payrollReportSummary.getCreatedDate()))
                .payrollApproved(payrollReportSummary.isPayrollApproved())
                .payrollSimulated(payrollReportSummary.isPayrollSimulation())
                .summary(summary)
                .build();
    }

    public static List<DashboardGraphResponse> transformToResponse(List<DashboardGraph> dashboardGraphList) {
        return  dashboardGraphList.stream()
                .map(x -> transformToResponse(x))
                .collect(Collectors.toList());
    }

    public static <T extends Serializable> byte[] serializeResponse(T report) {
        return SerializationUtils.serialize(report);
    }

    private static DashboardGraphResponse transformToResponse(DashboardGraph x){
        return DashboardGraphResponse.builder()
                .startDate(x.getStartDate())
                .endDate(x.getEndDate())
                .paymentFrequency(x.getPaymentFrequency())
                .netPay(x.getNetPay())
                .dateAdded(x.getDateAdded().toString())
                .build();
    }

    public static List<AuditTrailResponse> transformAuditTrail(List<AuditTrail> auditTrails){
        return auditTrails.stream().map(x -> AuditTrailResponse.builder()
                .companyId(x.getCompanyId())
                .event(x.getEvent())
                .details(x.getDetails())
                .employeeId(x.getEmployeeId())
                .name(x.getName())
                .dateTime(x.getDateTime().toString())
                .build()).collect(Collectors.toList());
    }
}


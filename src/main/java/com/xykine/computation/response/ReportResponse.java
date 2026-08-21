package com.xykine.computation.response;

import com.xykine.computation.entity.PayrollStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReportResponse {
    String reportId;
    String companyId;
    String offCycleId;
    String employeeId;
    String fullName;
    String employeeCode;
    String employeeHireDate;
    String departmentId;
    PayComputeDetailResponse detail;
    PayComputeSummaryResponse summary;
    PayrollStatus payrollStatus;
    String createdDate;
    String startDate;
    String endDate;
    boolean payrollSimulated;
    boolean offCycle;
    String payrollValidationError;
    String code;
}

package com.xykine.computation.response;

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
    String departmentId;
    PayComputeDetailResponse detail;
    PayComputeSummaryResponse summary;
    boolean payrollApproved;
    String createdDate;
    String startDate;
    String endDate;
    boolean payrollSimulated;
    boolean offCycle;
}

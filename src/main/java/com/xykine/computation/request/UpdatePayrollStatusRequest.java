package com.xykine.computation.request;

import com.xykine.computation.entity.PayrollStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UpdatePayrollStatusRequest {
    private UUID reportId;
    private String companyId;
    private PayrollStatus status;
}

package com.xykine.computation.request;

import com.xykine.computation.entity.PayrollStatus;
import lombok.Data;

import java.util.UUID;

@Data
public class CompletePayrollRequest {
    private UUID reportId;
    private String companyId;
}

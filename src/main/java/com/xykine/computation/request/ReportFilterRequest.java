package com.xykine.computation.request;

import com.xykine.computation.entity.PayrollStatus;
import com.xykine.computation.entity.PayrollType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.xykine.payroll.model.UserRole;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;


@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class ReportFilterRequest {
    @NotNull(message = "Company ID is required")
    private String companyId;
    private PayrollType payrollType;
    private PayrollStatus payrollStatus;
    private String startDate;
    private String endDate;
    private int page = 0;
    private int size = 10;
}
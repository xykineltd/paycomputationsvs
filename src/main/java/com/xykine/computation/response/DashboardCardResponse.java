package com.xykine.computation.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class DashboardCardResponse {
    private long totalOffCyclePayroll;
    private long totalRegularPayroll;
    private BigDecimal totalPayrollCost;
    private BigDecimal averageEmployeeCost;
    private String lastUpdatedAt;
}

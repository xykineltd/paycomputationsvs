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
    private long totalOffCyclePayroll = 0L;
    private long totalRegularPayroll = 0L;
    private BigDecimal totalPayrollCost = BigDecimal.ZERO;
    private BigDecimal averageEmployeeCost= BigDecimal.ZERO;
    private String lastUpdatedAt;
}

package com.xykine.computation.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.xykine.payroll.model.PaymentFrequencyEnum;

import java.math.BigDecimal;


@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class DashboardGraphResponse {
    private String startDate;
    private String endDate;
    private PaymentFrequencyEnum paymentFrequency;
    private BigDecimal netPay;
    private String dateAdded;
}

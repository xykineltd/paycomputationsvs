package com.xykine.computation.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;


@Data
@Builder
public class PaymentItemsAnalytics {
    private String companyId;
    private String reportId;

    // 📊 analytics
    private long totalCount;
    private long pending;
    private long processing;
    private long completed;
    private long failed;
    private long notProcessed;
    private long locked;
    private long readyToPay;
    private BigDecimal readyToPayAmount;
}
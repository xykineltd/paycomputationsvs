package com.xykine.computation.dto;

import lombok.Builder;
import lombok.Data;


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
}
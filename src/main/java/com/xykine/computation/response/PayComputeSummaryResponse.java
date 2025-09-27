package com.xykine.computation.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayComputeSummaryResponse implements Serializable {
    private String message;
    private Map<String, BigDecimal> summary;
    private Map<String, BigDecimal> summaryVariance;
    private ConcurrentHashMap<String, Set<SummaryDetail>> summaryDetails;
    private ConcurrentHashMap<String, Set<SummaryDetail>> summaryDetailsVariance;
}

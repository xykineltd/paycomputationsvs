package com.xykine.computation.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayComputeSummaryResponse implements Serializable {
    private String message;
    private Map<String, BigDecimal> summary;
    private Map<String, BigDecimal> summaryVariance;
    private Map<String, List<SummaryDetail>> summaryDetails;
    private Map<String, List<SummaryDetail>> summaryDetailsVariance;
}

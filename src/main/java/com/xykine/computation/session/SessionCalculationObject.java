package com.xykine.computation.session;

import com.xykine.computation.response.SummaryDetail;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
public class SessionCalculationObject {

    private ConcurrentHashMap<String, BigDecimal> summary;
    private ConcurrentHashMap<String, List<SummaryDetail>> summaryDetails;
    private Map<String, BigDecimal> computationConstants;

}

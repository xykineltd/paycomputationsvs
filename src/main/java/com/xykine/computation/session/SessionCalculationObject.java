package com.xykine.computation.session;

import com.xykine.computation.response.SummaryDetail;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
public class SessionCalculationObject {

    private  ConcurrentHashMap<String, BigDecimal> summary = new ConcurrentHashMap<>();
    private  ConcurrentHashMap<String, Set<SummaryDetail>> summaryDetails = new ConcurrentHashMap<>();
    private  ConcurrentHashMap<String, BigDecimal> computationConstants = new ConcurrentHashMap<>();
    private  Map<String, ConcurrentHashMap<String, BigDecimal>> costCenterSummary = new ConcurrentHashMap<>();
    private Map<String, List<String>> costCenters = new HashMap<>();

}

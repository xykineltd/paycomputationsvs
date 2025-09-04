package com.xykine.computation.session;

import com.xykine.computation.response.SummaryDetail;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
public class SessionCalculationObject {

    private  ConcurrentHashMap<String, BigDecimal> summary = new ConcurrentHashMap<>();
    private  ConcurrentHashMap<String, List<SummaryDetail>> summaryDetails = new ConcurrentHashMap<>();
    private  ConcurrentHashMap<String, BigDecimal> computationConstants = new ConcurrentHashMap<>();

}

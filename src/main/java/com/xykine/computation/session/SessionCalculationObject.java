package com.xykine.computation.session;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
public class SessionCalculationObject {

    private ConcurrentHashMap<String, BigDecimal> summary;
    private Map<String, BigDecimal> computationConstants;

}

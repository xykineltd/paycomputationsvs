package com.xykine.computation.session;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
public class SessionCalculationObject {

    private Map<String, BigDecimal> summary;
    private Map<String, BigDecimal> computationConstants;

}

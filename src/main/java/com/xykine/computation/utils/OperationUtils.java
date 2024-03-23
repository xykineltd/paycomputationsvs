package com.xykine.computation.utils;

import com.xykine.computation.model.MapKeys;
import com.xykine.computation.repo.ComputationConstantsRepo;
import com.xykine.computation.repo.TaxRepo;
import com.xykine.computation.session.SessionCalculationObject;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class OperationUtils {

    public static SessionCalculationObject doPreflight(SessionCalculationObject sessionCalculationObject, ComputationConstantsRepo computationConstantsRepo, TaxRepo taxRepo){

        Map<String, BigDecimal> sessionSummary = new HashMap<>();
        Map<String, BigDecimal> computationConstants = new HashMap<>();

        sessionSummary.put(MapKeys.TOTAL_NET_PAY, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_PAYEE_TAX, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_PENSION_FUND, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_PERSONAL_DEDUCTION, BigDecimal.ZERO);
        sessionCalculationObject.setSummary(sessionSummary);

        taxRepo.findAllByOrderByTaxClass().stream().forEach(x -> {
            computationConstants.put(x.getTaxClass(), x.getPercentage());
        });
        computationConstantsRepo.findAllByOrderById().stream().forEach(x->{
            computationConstants.put(x.getId(), x.getValue());
        });
        sessionCalculationObject.setComputationConstants(computationConstants);
        return sessionCalculationObject;
    }
}

package com.xykine.computation.utils;

import com.xykine.computation.repo.ComputationConstantsRepo;
import com.xykine.computation.repo.TaxRepo;
import com.xykine.computation.response.SummaryDetail;
import com.xykine.computation.session.SessionCalculationObject;
import org.xykine.payroll.model.MapKeys;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OperationUtils {

    public static SessionCalculationObject doPreflight(SessionCalculationObject sessionCalculationObject, ComputationConstantsRepo computationConstantsRepo, TaxRepo taxRepo){

        ConcurrentHashMap<String, BigDecimal> sessionSummary = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, List<SummaryDetail>> sessionSummaryDetails = new ConcurrentHashMap<>();
        Map<String, BigDecimal> computationConstants = new HashMap<>();

        sessionSummary.put(MapKeys.TOTAL_NET_PAY, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_GROSS_PAY, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_PAYEE_TAX, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_EMPLOYEE_PENSION_CONTRIBUTION, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_NHF, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_PERSONAL_DEDUCTION, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_EMPLOYER_PENSION_CONTRIBUTION, BigDecimal.ZERO);
        sessionCalculationObject.setSummary(sessionSummary);

        sessionSummaryDetails.put(MapKeys.TOTAL_NET_PAY, new ArrayList<>());
        sessionSummaryDetails.put(MapKeys.TOTAL_GROSS_PAY, new ArrayList<>());
        sessionSummaryDetails.put(MapKeys.TOTAL_PAYEE_TAX, new ArrayList<>());
        sessionSummaryDetails.put(MapKeys.TOTAL_EMPLOYEE_PENSION_CONTRIBUTION, new ArrayList<>());
        sessionSummaryDetails.put(MapKeys.TOTAL_NHF, new ArrayList<>());
        sessionSummaryDetails.put(MapKeys.TOTAL_PERSONAL_DEDUCTION, new ArrayList<>());
        sessionSummaryDetails.put(MapKeys.TOTAL_EMPLOYER_PENSION_CONTRIBUTION, new ArrayList<>());
        sessionCalculationObject.setSummaryDetails(sessionSummaryDetails);

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

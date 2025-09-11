package com.xykine.computation.utils;

import com.xykine.computation.controller.Compute;
import com.xykine.computation.repo.ComputationConstantsRepo;
import com.xykine.computation.repo.EmployeeMetadataRepo;
import com.xykine.computation.repo.TaxRepo;
import com.xykine.computation.request.PaymentInfoRequest;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.response.SummaryDetail;
import com.xykine.computation.service.EmployeeMetadataService;
import com.xykine.computation.session.SessionCalculationObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xykine.payroll.model.MapKeys;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OperationUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(Compute.class);

    public static SessionCalculationObject doPreflight(SessionCalculationObject sessionCalculationObject,
                                                       ComputationConstantsRepo computationConstantsRepo,
                                                       TaxRepo taxRepo, EmployeeMetadataService employeeMetadataService, PaymentInfoRequest paymentRequest){

        ConcurrentHashMap<String, BigDecimal> sessionSummary = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, List<SummaryDetail>> sessionSummaryDetails = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, BigDecimal> computationConstants = new ConcurrentHashMap<>();

        sessionSummary.put(MapKeys.TOTAL_NET_PAY, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_GROSS_PAY, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_PAYEE_TAX, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_EMPLOYEE_PENSION_CONTRIBUTION, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_NHF, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_PERSONAL_DEDUCTION, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_EMPLOYER_PENSION_CONTRIBUTION, BigDecimal.ZERO);
        sessionCalculationObject.setSummary(sessionSummary);

        sessionSummaryDetails.put(MapKeys.TOTAL_NET_PAY, Collections.synchronizedList(new ArrayList<>()));
        sessionSummaryDetails.put(MapKeys.TOTAL_GROSS_PAY, Collections.synchronizedList(new ArrayList<>()));
        sessionSummaryDetails.put(MapKeys.TOTAL_PAYEE_TAX, Collections.synchronizedList(new ArrayList<>()));
        sessionSummaryDetails.put(MapKeys.TOTAL_EMPLOYEE_PENSION_CONTRIBUTION, Collections.synchronizedList(new ArrayList<>()));
        sessionSummaryDetails.put(MapKeys.TOTAL_NHF, Collections.synchronizedList(new ArrayList<>()));
        sessionSummaryDetails.put(MapKeys.TOTAL_PERSONAL_DEDUCTION, Collections.synchronizedList(new ArrayList<>()));
        sessionSummaryDetails.put(MapKeys.TOTAL_EMPLOYER_PENSION_CONTRIBUTION, Collections.synchronizedList(new ArrayList<>()));
        sessionCalculationObject.setSummaryDetails(sessionSummaryDetails);

        LOGGER.debug(" ========> tax Repo {} ", taxRepo.findAllByOrderByTaxClass());

        employeeMetadataService.preloadAllIntoCache(paymentRequest.getCompanyId());

        taxRepo.findAllByOrderByTaxClass().forEach(x -> {
                computationConstants.put(x.getTaxClass(), x.getPercentage());
            });
        computationConstantsRepo.findAllByOrderById().forEach(x->{
            computationConstants.put(x.getId(), x.getValue());
        });
        sessionCalculationObject.setComputationConstants(computationConstants);
        return sessionCalculationObject;
    }

    public static PaymentComputeResponse refineResponse(PaymentComputeResponse paymentComputeResponse,
                                                        SessionCalculationObject sessionCalculationObject,
                                                        PaymentInfoRequest paymentRequest) {
        paymentComputeResponse.setId(UUID.randomUUID());
        paymentComputeResponse.setSummary(sessionCalculationObject.getSummary());
        paymentComputeResponse.setSummaryDetails(sessionCalculationObject.getSummaryDetails());
        paymentComputeResponse.setStart(paymentRequest.getStart().toString());
        paymentComputeResponse.setEnd(paymentRequest.getEnd().toString());
        paymentComputeResponse.setPayrollSimulation(paymentRequest.isPayrollSimulation());
        paymentComputeResponse.setOffCycle(paymentRequest.isOffCycle());
        paymentComputeResponse.setOffCycleId(paymentRequest.getOffCycleID());
        return paymentComputeResponse;
    }
}

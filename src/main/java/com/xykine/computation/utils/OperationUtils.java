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
                                                       EmployeeMetadataService employeeMetadataService, PaymentInfoRequest paymentRequest){

        ConcurrentHashMap<String, BigDecimal> sessionSummary = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, Set<SummaryDetail>> sessionSummaryDetails = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, BigDecimal> computationConstants = new ConcurrentHashMap<>();
        Map<String, ConcurrentHashMap<String, BigDecimal>> costCenterSummary = new ConcurrentHashMap<>();

        sessionSummary.put(MapKeys.TOTAL_NET_PAY, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_GROSS_PAY, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_PAYEE_TAX, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_EMPLOYEE_PENSION_CONTRIBUTION, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_NHF, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_PERSONAL_DEDUCTION, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_EMPLOYER_PENSION_CONTRIBUTION, BigDecimal.ZERO);
        sessionCalculationObject.setSummary(sessionSummary);

        Set<String> costCenters = sessionCalculationObject.getCostCenters().keySet();
        if (!costCenters.isEmpty()) {
            for (String costCenter : costCenters) {
                ConcurrentHashMap<String, BigDecimal> costCenterNetPay = new ConcurrentHashMap<>();
                costCenterNetPay.put(MapKeys.TOTAL_NET_PAY, BigDecimal.ZERO);
                costCenterNetPay.put(MapKeys.TOTAL_GROSS_PAY, BigDecimal.ZERO);
                costCenterNetPay.put(MapKeys.TOTAL_PAYEE_TAX, BigDecimal.ZERO);
                costCenterNetPay.put(MapKeys.TOTAL_EMPLOYEE_PENSION_CONTRIBUTION, BigDecimal.ZERO);
                costCenterNetPay.put(MapKeys.TOTAL_NHF, BigDecimal.ZERO);
                costCenterNetPay.put(MapKeys.TOTAL_PERSONAL_DEDUCTION, BigDecimal.ZERO);
                costCenterNetPay.put(MapKeys.TOTAL_EMPLOYER_PENSION_CONTRIBUTION, BigDecimal.ZERO);
                costCenterSummary.put(costCenter, costCenterNetPay);
            }
        }
        sessionCalculationObject.setCostCenterSummary(costCenterSummary);

        sessionSummaryDetails.put(MapKeys.TOTAL_NET_PAY, Collections.synchronizedSet(new HashSet<>()));
        sessionSummaryDetails.put(MapKeys.TOTAL_GROSS_PAY,Collections.synchronizedSet(new HashSet<>()));
        sessionSummaryDetails.put(MapKeys.TOTAL_PAYEE_TAX, Collections.synchronizedSet(new HashSet<>()));
        sessionSummaryDetails.put(MapKeys.TOTAL_EMPLOYEE_PENSION_CONTRIBUTION, Collections.synchronizedSet(new HashSet<>()));
        sessionSummaryDetails.put(MapKeys.TOTAL_NHF, Collections.synchronizedSet(new HashSet<>()));
        sessionSummaryDetails.put(MapKeys.TOTAL_PERSONAL_DEDUCTION,Collections.synchronizedSet(new HashSet<>()));
        sessionSummaryDetails.put(MapKeys.TOTAL_EMPLOYER_PENSION_CONTRIBUTION, Collections.synchronizedSet(new HashSet<>()));
        sessionCalculationObject.setSummaryDetails(sessionSummaryDetails);

        employeeMetadataService.preloadAllIntoCache(paymentRequest.getCompanyId());

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
        paymentComputeResponse.setCostCenterSummary(sessionCalculationObject.getCostCenterSummary());
        paymentComputeResponse.setStart(paymentRequest.getStart().toString());
        paymentComputeResponse.setEnd(paymentRequest.getEnd().toString());
        paymentComputeResponse.setPayrollSimulation(paymentRequest.isPayrollSimulation());
        paymentComputeResponse.setOffCycle(paymentRequest.isOffCycle());
        paymentComputeResponse.setOffCycleId(paymentRequest.getOffCycleID());
        return paymentComputeResponse;
    }
}

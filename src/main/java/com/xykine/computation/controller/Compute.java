package com.xykine.computation.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.xykine.computation.model.MapKeys;
import com.xykine.computation.service.ReportPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xykine.computation.request.PaymentInfoRequest;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.service.ComputeService;
import com.xykine.computation.session.SessionCalculationObject;

@RestController
@RequestMapping("/compute")
@RequiredArgsConstructor
public class Compute {

    private final ComputeService computeService;
    private final SessionCalculationObject sessionCalculationObject;
    private final ReportPersistenceService reportPersistenceService;

    @PostMapping("/payroll")
    public PaymentComputeResponse computePayroll(@RequestBody PaymentInfoRequest paymentRequest) throws IOException, ClassNotFoundException {
        Map<String, BigDecimal> sessionSummary = new HashMap<>();
        sessionSummary.put(MapKeys.TOTAL_NET_PAY, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_PAYEE_TAX, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_PENSION_FUND, BigDecimal.ZERO);
        sessionSummary.put(MapKeys.TOTAL_PERSONAL_DEDUCTION, BigDecimal.ZERO);
        sessionCalculationObject.setSummary(sessionSummary);

        PaymentComputeResponse paymentComputeResponse = computeService.computePayroll(paymentRequest);

        paymentComputeResponse.setSummary(sessionCalculationObject.getSummary());
        paymentComputeResponse.setStart(paymentRequest.getStart().toString());
        paymentComputeResponse.setEnd(paymentRequest.getEnd().toString());
        paymentComputeResponse.setPayrollSimulation(paymentRequest.isPayrollSimulation());
        paymentComputeResponse.setCreatedDate(LocalDate.now().toString());

        reportPersistenceService.serializeAndSaveReport(paymentComputeResponse);

        return paymentComputeResponse;
    }
}

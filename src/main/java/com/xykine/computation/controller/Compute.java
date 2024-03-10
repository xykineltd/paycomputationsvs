package com.xykine.computation.controller;

import java.math.BigDecimal;
import java.util.HashMap;

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

    @PostMapping("/payroll")
    public PaymentComputeResponse computePayroll(@RequestBody PaymentInfoRequest paymentRequest) {

        sessionCalculationObject.setEmployerBornTaxDetails(new HashMap<String, BigDecimal>());
        sessionCalculationObject.setTotalAmount(BigDecimal.ZERO);

        PaymentComputeResponse paymentComputeResponse = computeService.computePayroll(paymentRequest);
        paymentComputeResponse.setEmployerBornTaxDetail(sessionCalculationObject.getEmployerBornTaxDetails());
        paymentComputeResponse.setTotalAmount(sessionCalculationObject.getTotalAmount());
        paymentComputeResponse.setStart(paymentRequest.getStart());
        paymentComputeResponse.setEnd(paymentRequest.getEnd());

        return computeService.computePayroll(paymentRequest);
    }
}

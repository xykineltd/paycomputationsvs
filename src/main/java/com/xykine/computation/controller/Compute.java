package com.xykine.computation.controller;

import com.xykine.computation.request.PaymentComputeRequest;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.service.ComputeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/compute")
@RequiredArgsConstructor
public class Compute {

    private final ComputeService computeService;

    @PostMapping("/payroll")
    public PaymentComputeResponse computePayroll(@RequestBody PaymentComputeRequest paymentRequest) {
        return computeService.computePayroll(paymentRequest);
    }
}

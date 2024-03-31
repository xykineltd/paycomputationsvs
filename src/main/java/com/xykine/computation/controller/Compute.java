package com.xykine.computation.controller;

import java.io.IOException;
import java.util.UUID;


import com.xykine.computation.repo.ComputationConstantsRepo;
import com.xykine.computation.repo.TaxRepo;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.service.ReportPersistenceService;
import com.xykine.computation.utils.OperationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ReportPersistenceService reportPersistenceService;
    private final ComputationConstantsRepo computationConstantsRepo;
    private final TaxRepo taxRepo;
    @Autowired
    private SessionCalculationObject sessionCalculationObject;

    @PostMapping("/payroll")
    public ReportResponse computePayroll(@RequestBody PaymentInfoRequest paymentRequest) throws IOException, ClassNotFoundException {

        sessionCalculationObject = OperationUtils.doPreflight(sessionCalculationObject, computationConstantsRepo, taxRepo);
        PaymentComputeResponse paymentComputeResponse = computeService.computePayroll(paymentRequest);
        paymentComputeResponse.setId(UUID.randomUUID());
        paymentComputeResponse.setSummary(sessionCalculationObject.getSummary());
        paymentComputeResponse.setStart(paymentRequest.getStart().toString());
        paymentComputeResponse.setEnd(paymentRequest.getEnd().toString());
        paymentComputeResponse.setPayrollSimulation(paymentRequest.isPayrollSimulation());
        return reportPersistenceService.serializeAndSaveReport(paymentComputeResponse, paymentRequest.getCompanyId());

    }


}

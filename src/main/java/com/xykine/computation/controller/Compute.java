package com.xykine.computation.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;


import com.xykine.computation.repo.ComputationConstantsRepo;
import com.xykine.computation.repo.TaxRepo;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.service.AdminService;
import com.xykine.computation.service.ReportPersistenceService;
import com.xykine.computation.utils.OperationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.xykine.computation.request.PaymentInfoRequest;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.service.ComputeService;
import com.xykine.computation.session.SessionCalculationObject;
import org.xykine.payroll.model.PaymentInfo;

@RestController
@RequestMapping("/compute")
@RequiredArgsConstructor
public class Compute {

    private final ComputeService computeService;
    private final ReportPersistenceService reportPersistenceService;
    private final ComputationConstantsRepo computationConstantsRepo;
    private final TaxRepo taxRepo;
    private final AdminService adminService;

    @Autowired
    private SessionCalculationObject sessionCalculationObject;

    @PostMapping("/payroll")
    public ReportResponse computePayroll(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody PaymentInfoRequest paymentRequest) throws IOException, ClassNotFoundException {
        System.out.println("Authorization===>" + authorizationHeader);

        sessionCalculationObject = OperationUtils.doPreflight(sessionCalculationObject, computationConstantsRepo, taxRepo);
        List<PaymentInfo> rawInfo = adminService.getPaymentInfoList(paymentRequest, authorizationHeader);
        PaymentComputeResponse paymentComputeResponse = computeService.computePayroll(rawInfo);
        paymentComputeResponse.setId(UUID.randomUUID());
        paymentComputeResponse.setSummary(sessionCalculationObject.getSummary());
        paymentComputeResponse.setSummaryDetails(sessionCalculationObject.getSummaryDetails());
        paymentComputeResponse.setStart(paymentRequest.getStart().toString());
        paymentComputeResponse.setEnd(paymentRequest.getEnd().toString());
        paymentComputeResponse.setPayrollSimulation(paymentRequest.isPayrollSimulation());
        paymentComputeResponse.setOffCycle(paymentRequest.isOffCycle());
        paymentComputeResponse.setOffCycleId(paymentRequest.getOffCycleID());
        return reportPersistenceService.serializeAndSaveReport(paymentComputeResponse, paymentRequest.getCompanyId());
    }
}

package com.xykine.computation.controller;

import com.xykine.computation.exceptions.PayrollValidationException;
import com.xykine.computation.repo.ComputationConstantsRepo;
import com.xykine.computation.repo.TaxRepo;
import com.xykine.computation.request.PaymentInfoRequest;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.service.AdminService;
import com.xykine.computation.service.ComputeService;
import com.xykine.computation.service.ReportPersistenceService;
import com.xykine.computation.session.SessionCalculationObject;
import com.xykine.computation.utils.OperationUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/compute")
@RequiredArgsConstructor
public class Compute {

    private static final Logger LOGGER = LoggerFactory.getLogger(Compute.class);

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
            @RequestBody PaymentInfoRequest paymentRequest) {
        try{
            sessionCalculationObject = OperationUtils.doPreflight(sessionCalculationObject, computationConstantsRepo, taxRepo);
            List rawInfo = adminService.getPaymentInfoList(paymentRequest, authorizationHeader);
            LOGGER.info("rawInfo*****************************{}", rawInfo);
            LOGGER.debug("authorizationHeader*****************************{}", authorizationHeader);
            assert rawInfo != null;
            PaymentComputeResponse paymentComputeResponse = computeService.computePayroll(rawInfo);
            paymentComputeResponse = OperationUtils.refineResponse(paymentComputeResponse, sessionCalculationObject, paymentRequest);
            return reportPersistenceService.serializeAndSaveReport(paymentComputeResponse, paymentRequest.getCompanyId());
        } catch (Exception ex) {
            LOGGER.info(ex.getMessage());
            ex.printStackTrace();

            throw new PayrollValidationException(ex.getMessage());
        }
    }
}

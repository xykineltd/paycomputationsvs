package com.xykine.computation.controller;

import java.io.IOException;
import java.util.List;


import com.xykine.computation.config.CurrentUser;
import com.xykine.computation.config.CustomUserDetails;
import com.xykine.computation.exceptions.PayrollUnmodifiableException;
import com.xykine.computation.exceptions.PayrollValidationException;
import com.xykine.computation.repo.ComputationConstantsRepo;
import com.xykine.computation.repo.TaxRepo;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.service.AdminService;
import com.xykine.computation.service.ReportPersistenceService;
import com.xykine.computation.utils.OperationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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
        try{
            sessionCalculationObject = OperationUtils.doPreflight(sessionCalculationObject, computationConstantsRepo, taxRepo);
            List rawInfo = adminService.getPaymentInfoList(paymentRequest, authorizationHeader);
            assert rawInfo != null;
            PaymentComputeResponse paymentComputeResponse = computeService.computePayroll(rawInfo);
            paymentComputeResponse = OperationUtils.refineResponse(paymentComputeResponse, sessionCalculationObject, paymentRequest);
            return reportPersistenceService.serializeAndSaveReport(paymentComputeResponse, paymentRequest.getCompanyId());
        } catch (Exception ex) {
            throw new PayrollValidationException(ex.getMessage());
        }
    }
}

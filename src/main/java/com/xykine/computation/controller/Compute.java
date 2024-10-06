package com.xykine.computation.controller;

import java.io.IOException;
import java.util.List;


import com.xykine.computation.config.CurrentUser;
import com.xykine.computation.config.CustomUserDetails;
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
    public ResponseEntity<ReportResponse> computePayroll(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody PaymentInfoRequest paymentRequest) throws IOException, ClassNotFoundException {

        sessionCalculationObject = OperationUtils.doPreflight(sessionCalculationObject, computationConstantsRepo, taxRepo);
        ResponseEntity<List> responseEntity = adminService.getPaymentInfoList(paymentRequest, authorizationHeader);
        List<PaymentInfo> rawInfo = responseEntity.getBody();
        // Extract Payroll-Errors header
        String payrollErrors = responseEntity.getHeaders().getFirst("Payroll-Errors");
        HttpHeaders responseHeaders = new HttpHeaders();

        // Check for Payroll-Errors and return an error response if necessary
        if (payrollErrors != null && !payrollErrors.isEmpty() && !payrollErrors.equals("No Errors found")) {
            ReportResponse errorResponse = new ReportResponse();
            errorResponse.setPayrollValidationError(payrollErrors);
            return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        assert rawInfo != null;
        PaymentComputeResponse paymentComputeResponse = computeService.computePayroll(rawInfo);
        paymentComputeResponse = OperationUtils.refineResponse(paymentComputeResponse, sessionCalculationObject, paymentRequest);

        var responseBody = reportPersistenceService.serializeAndSaveReport(paymentComputeResponse, paymentRequest.getCompanyId());
        return new ResponseEntity<>(responseBody, responseHeaders, HttpStatus.OK);
    }
}

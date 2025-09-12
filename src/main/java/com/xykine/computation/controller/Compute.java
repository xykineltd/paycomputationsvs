package com.xykine.computation.controller;

import com.xykine.computation.exceptions.PayrollValidationException;
import com.xykine.computation.repo.ComputationConstantsRepo;
import com.xykine.computation.repo.TaxRepo;
import com.xykine.computation.request.PaymentInfoRequest;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.response.ReportResponse;
import com.xykine.computation.service.AdminService;
import com.xykine.computation.service.ComputeService;
import com.xykine.computation.service.EmployeeMetadataService;
import com.xykine.computation.service.ReportPersistenceService;
import com.xykine.computation.session.SessionCalculationObject;
import com.xykine.computation.utils.OperationUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.xykine.payroll.model.PaymentInfo;

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
    private final EmployeeMetadataService employeeMetadataService;

    @Autowired
    private SessionCalculationObject sessionCalculationObject;

    @PostMapping("/payroll")
    public ReportResponse computePayroll(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody PaymentInfoRequest paymentRequest) {

        try {
            sessionCalculationObject = OperationUtils.doPreflight(
                    sessionCalculationObject,
                    computationConstantsRepo,
                    taxRepo,
                    employeeMetadataService,
                    paymentRequest
            );

            List<PaymentInfo> paymentInfoList = adminService.getPaymentInfoList(paymentRequest, authorizationHeader);
            if (paymentInfoList == null || paymentInfoList.isEmpty()) {
                throw new PayrollValidationException("No payment information found for request");
            }

            PaymentComputeResponse computeResponse = computeService.computePayroll(paymentInfoList);

            // Refine response
            computeResponse = OperationUtils.refineResponse(computeResponse, sessionCalculationObject, paymentRequest);

            // Persist and return final report
            return reportPersistenceService.serializeAndSaveReport(computeResponse, paymentRequest.getCompanyId());

        } catch (PayrollValidationException ex) {
            LOGGER.warn("Payroll validation failed: {}", ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            LOGGER.error("Unexpected error during payroll computation", ex);
            throw new PayrollValidationException("Failed to compute payroll. Please contact support.");
        }
    }
}
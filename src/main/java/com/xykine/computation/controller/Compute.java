package com.xykine.computation.controller;

import com.xykine.computation.domain.JobStatus;
import com.xykine.computation.repo.ComputationConstantsRepo;
import com.xykine.computation.request.PaymentInfoRequest;
import com.xykine.computation.service.*;
import com.xykine.computation.session.SessionCalculationObject;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/compute")
@RequiredArgsConstructor
public class Compute extends TextWebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(Compute.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ComputeService computeService;
    private final ReportPersistenceService reportPersistenceService;
    private final ComputationConstantsRepo computationConstantsRepo;
    private final AdminService adminService;
    private final EmployeeMetadataService employeeMetadataService;
    private final JobStatusStore jobStatusStore;


    @Autowired
    private SessionCalculationObject sessionCalculationObject;

    @PostMapping("/payroll/start")
    public ResponseEntity<Map<String, String>> startPayroll(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody PaymentInfoRequest paymentRequest) {

        computeService.ensurePayrollConfiguration(paymentRequest.getCompanyId());

        if (!paymentRequest.isPayrollSimulation() || !paymentRequest.isOffCycle()) {
            computeService.validatePayrollIsNotCompleted(
                    String.valueOf(paymentRequest.getStart()),
                    paymentRequest.getCompanyId()
            );
        }

        String jobId = UUID.randomUUID().toString();
        jobStatusStore.createJob(jobId);

        // Run asynchronously
        if(paymentRequest.isOffCycle()) {
            reportPersistenceService.computeOffCyclePayrollAsync(
                    progress ->
                            messagingTemplate.convertAndSend("/topic/job-status", progress),
                    jobId, authorizationHeader, paymentRequest);
        } else {
            reportPersistenceService.computePayrollAsync(
                    progress ->
                            messagingTemplate.convertAndSend("/topic/job-status", progress),
                    jobId, authorizationHeader, paymentRequest);
        }

        Map<String, String> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("status", "ACCEPTED");

        return ResponseEntity.accepted().body(response);
    }

    // Use jobId to check job status
    @GetMapping("/payroll/status/{jobId}")
    public Mono<ResponseEntity<JobStatus>> getPayrollStatus(@PathVariable String jobId) {
        JobStatus status = jobStatusStore.getJob(jobId);
        if (status == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return Mono.just(ResponseEntity.ok(status));
    }
}
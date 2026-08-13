package com.xykine.computation.controller;

import com.xykine.computation.reconciliation.run.PayrollReconciliationRunService;
import com.xykine.computation.reconciliation.run.ReconciliationAnalyticsResponse;
import com.xykine.computation.reconciliation.run.ReconciliationDetailsResponse;
import com.xykine.computation.reconciliation.run.StageRunResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/compute/payroll-reconciliation")
public class PayrollReconciliationRunController {

    private static final Logger log = LoggerFactory.getLogger(PayrollReconciliationRunController.class);

    private final PayrollReconciliationRunService runService;

    public PayrollReconciliationRunController(PayrollReconciliationRunService runService) {
        this.runService = runService;
    }

    /**
     * Run Input Alignment: upload Excel, complete-replace temp raw rows, then compare input fields.
     * Loads sheet aliases from reconciliationMappings by companyId.
     */
    @PostMapping(value = "/input-alignment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StageRunResponse runInputAlignment(
            @RequestParam String companyId,
            @RequestParam String reportId,
            @RequestParam(required = false) String legalEntityId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String token
    ) {
        log.info("Run input alignment companyId={} reportId={} legalEntityId={} file={}",
                companyId, reportId, legalEntityId, file != null ? file.getOriginalFilename() : null);
        return runService.runInputAlignment(companyId, reportId, legalEntityId, file, token);
    }

    @PostMapping("/{reconciliationId}/outcome-variance")
    public StageRunResponse runOutcomeVariance(
            @PathVariable String reconciliationId,
            @RequestHeader("Authorization") String token
    ) {
        log.info("Run outcome variance reconciliationId={}", reconciliationId);
        return runService.runOutcomeVariance(reconciliationId, token);
    }

    @GetMapping("/{reconciliationId}/analytics")
    public ReconciliationAnalyticsResponse getAnalytics(@PathVariable String reconciliationId) {
        return runService.getAnalytics(reconciliationId);
    }

    @GetMapping("/{reconciliationId}/details")
    public ReconciliationDetailsResponse getDetails(
            @PathVariable String reconciliationId,
            @RequestParam String stage,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return runService.getDetails(reconciliationId, stage, status, page, size);
    }
}

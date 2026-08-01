package com.xykine.computation.payrollreconciliation.controller;

import com.xykine.computation.payrollreconciliation.dto.*;
import com.xykine.computation.payrollreconciliation.entity.ReconciliationMapping;
import com.xykine.computation.payrollreconciliation.entity.ReconciliationRun;
import com.xykine.computation.payrollreconciliation.repo.ReconciliationRunRepo;
import com.xykine.computation.payrollreconciliation.service.PayrollReconciliationService;
import com.xykine.computation.utils.CompanyAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/compute/payroll-reconciliation")
@RequiredArgsConstructor
public class PayrollReconciliationController {

    private final PayrollReconciliationService reconciliationService;
    private final ReconciliationRunRepo runRepo;
    private final CompanyAccessGuard companyAccessGuard;

    @GetMapping("/mapping")
    public ReconciliationMapping getMapping(@RequestParam String companyId) {
        companyAccessGuard.requireCompanyAccess(companyId);
        return reconciliationService.getMapping(companyId);
    }

    @PutMapping("/mapping")
    public ReconciliationMapping saveMapping(@RequestParam String companyId,
                                             @RequestBody ReconciliationMapping body) {
        companyAccessGuard.requireCompanyAccess(companyId);
        return reconciliationService.saveMapping(companyId, body);
    }

    @GetMapping("/mapping/status")
    public MappingStatusResponse mappingStatus(@RequestParam String companyId) {
        companyAccessGuard.requireCompanyAccess(companyId);
        return reconciliationService.getMappingStatus(companyId);
    }

    @PostMapping("/upload")
    public UploadResponse upload(@RequestParam("companyId") String companyId,
                                 @RequestParam("reportId") String reportId,
                                 @RequestParam("legalEntityId") String legalEntityId,
                                 @RequestParam("file") MultipartFile file) {
        companyAccessGuard.requireCompanyAccess(companyId);
        return reconciliationService.upload(companyId, reportId, legalEntityId, file);
    }

    @PostMapping("/{id}/input-alignment")
    public StageResultResponse inputAlignment(@PathVariable String id,
                                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireRunCompanyAccess(id);
        return reconciliationService.runInputAlignment(id, authorization);
    }

    @PostMapping("/{id}/outcome-variance")
    public ResponseEntity<?> outcomeVariance(@PathVariable String id,
                                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireRunCompanyAccess(id);
        try {
            return ResponseEntity.ok(reconciliationService.runOutcomeVariance(id, authorization));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(MapOf("message", ex.getMessage()));
        }
    }

    @GetMapping("/{id}/analytics")
    public AnalyticsResponse analytics(@PathVariable String id) {
        requireRunCompanyAccess(id);
        return reconciliationService.getAnalytics(id);
    }

    @GetMapping("/{id}/details")
    public PagedDetailsResponse details(@PathVariable String id,
                                        @RequestParam String stage,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        requireRunCompanyAccess(id);
        return reconciliationService.getDetails(id, stage, status, page, size);
    }

    private void requireRunCompanyAccess(String runId) {
        ReconciliationRun run = runRepo.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation run not found: " + runId));
        companyAccessGuard.requireCompanyAccess(run.getCompanyId());
    }

    private static java.util.Map<String, String> MapOf(String k, String v) {
        return java.util.Map.of(k, v);
    }
}

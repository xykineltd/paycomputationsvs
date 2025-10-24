package com.xykine.computation.controller;

import com.xykine.computation.entity.Stage;
import com.xykine.computation.entity.StageEntity;
import com.xykine.computation.entity.StageInstance;
import com.xykine.computation.request.*;
import com.xykine.computation.request.WorkflowDTOs.CompleteStageRequest;
import com.xykine.computation.request.WorkflowDTOs.CreateStageInstanceRequest;
import com.xykine.computation.request.WorkflowDTOs.CreateStageRequest;
import com.xykine.computation.request.WorkflowDTOs.UpdateStageRequest;
import com.xykine.computation.service.ApprovalService;
import com.xykine.computation.service.StageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/compute")
@RequiredArgsConstructor
public class ApprovalController {

    private final StageService stageService;
    private final ApprovalService approvalService;

    // ---- Stages ----

    @PostMapping("/stages")
    public Stage createStage(@Valid @RequestBody CreateStageRequest req) {
        Stage s = Stage.builder()
                .entity(StageEntity.valueOf(req.getEntity()))
                .stepNumber(req.getStepNumber())
                .name(req.getName())
                .description(req.getDescription())
                .companyId(req.getCompanyId())
                .approverId(req.getApproverId())
                .approverName(req.getApproverName())
                .actions(req.getActions())
                .createdAt(Instant.now())
                .build();
        return stageService.createStage(s);
    }

    @PutMapping("/stages/{id}")
    public Stage updateStage(@PathVariable String id, @Valid @RequestBody UpdateStageRequest req) {
        return stageService.updateStage(id, req.getName(), req.getDescription(), req.getApproverId(), req.getActions());
    }

    @DeleteMapping("/stages/{id}")
    public ResponseEntity<Boolean> deleteStage(@PathVariable String id) {
        return ResponseEntity.ok (stageService.deleteStage(id));
    }

    @GetMapping("/stages")
    public List<Stage> getOrderedStages(@RequestParam String entity, String companyId) {
        return stageService.getOrderedStages(StageEntity.valueOf(entity), companyId);
    }

    // ---- Stage Instances / Approvals ----

    @PostMapping("/approvals/instances")
    public StageInstance createStageInstance(@Valid @RequestBody CreateStageInstanceRequest req) {
        return approvalService.createStageInstance(UUID.fromString(req.getPayrollId()), req.getStageId(), req.getExecutedById(), req.getDueDate());
    }

    @GetMapping("/approvals/instances/current")
    public StageInstance getCurrentStageInstance(@RequestParam String payrollId) {
        return approvalService.getCurrentStageInstance(UUID.fromString(payrollId));
    }

    @PostMapping("/approvals/instances/{id}/complete")
    public StageInstance completeStage(@PathVariable String id, @Valid @RequestBody CompleteStageRequest req) {
        return approvalService.completeStage(id, req.getApprove(), req.getExecutedById(), req.getRemarks());
    }

    @PostMapping("/approvals/instances/next")
    public StageInstance createNextStageInstance(@Valid @RequestBody CreateNextStageInstanceRequest req) {
        return approvalService.createNextStageInstance(UUID.fromString(req.getPayrollId()), req.getExecutedById(), req.getDueDate());
    }
}

package com.xykine.computation.controller;


import com.xykine.computation.entity.ApprovalStatus;
import com.xykine.computation.entity.PayrollStatus;
import com.xykine.computation.entity.StageEntity;
import com.xykine.computation.entity.StageInstance;
import com.xykine.computation.request.WorkflowDTOs.CompleteWorkflowStageInstanceRequest;
import com.xykine.computation.request.WorkflowDTOs.StartWorkflowRequest;
import com.xykine.computation.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/compute/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    // POST /api/workflows/start
    @PostMapping("/start")
    public List<StageInstance> startWorkflow(@Valid @RequestBody StartWorkflowRequest req) {
        StageEntity entity = StageEntity.valueOf(req.getEntity());
        return workflowService.startWorkflow(
                entity,
                req.getCompanyId(),
                req.getUserId(),
                req.getPayrollId()
        );
    }

    // POST /workflows/instances/{id}/complete
    @PostMapping("/instances/{id}/complete")
    public StageInstance complete(@PathVariable String id,
                                  @Valid @RequestBody CompleteWorkflowStageInstanceRequest req, @RequestHeader("Authorization") String token) {
        PayrollStatus status = PayrollStatus.valueOf(req.getStatus());
        return workflowService.completeWorkflowStageInstanceAndReport(
                id, status, req.getExecutedById(), req.getRemarks(), req.getNextDueDate(),  token
        );
    }
}

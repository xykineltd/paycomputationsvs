package com.xykine.computation.service;

        import com.xykine.computation.entity.*;
        import com.xykine.computation.repo.StageInstanceRepository;
        import lombok.RequiredArgsConstructor;
        import org.springframework.stereotype.Service;

        import java.time.Instant;
        import java.util.List;
        import java.util.Objects;
        import java.util.UUID;

@Service @RequiredArgsConstructor
public class ApprovalService {
    private final StageService stageService;
    private final StageInstanceRepository instanceRepo;

    public StageInstance createStageInstance(UUID payrollId, String stageId, String executedById, Instant dueDate) {
        Stage stage = stageService.getById(stageId);

        StageInstance instance = StageInstance.builder()
                .payrollId(payrollId)
                .stageId(stage.getId())
                .entity(stage.getEntity())
                .stepNumber(stage.getStepNumber())
                .name(stage.getName())
                .status(PayrollStatus.PENDING)
                .approverId(stage.getApproverId())
                .executedById(executedById)
                .dueDate(dueDate)
                .createdAt(Instant.now())
                .build();

        return instanceRepo.save(instance);
    }

    public StageInstance getCurrentStageInstance(UUID payrollId) {
        // Current = the highest-step instance that is not terminal (APPROVED/REJECTED), or latest created
        return instanceRepo.findFirstByPayrollIdAndStatusInOrderByStepNumberDescCreatedAtDesc(
                        payrollId, List.of(PayrollStatus.PENDING, PayrollStatus.IN_PROGRESS))
                .orElseGet(() ->
                        instanceRepo.findFirstByPayrollIdOrderByStepNumberDesc(payrollId)
                                .orElse(null)
                );
    }

    public StageInstance startIfPending(StageInstance instance, String actorId) {
        if (instance.getStatus() == PayrollStatus.PENDING) {
            instance.setStatus(PayrollStatus.IN_PROGRESS);
            instance.setStartedAt(Instant.now());
            instance.setExecutedById(actorId);
            instance = instanceRepo.save(instance);
        }
        return instance;
    }

    public StageInstance completeStage(String instanceId, boolean approve, String executedById, String remarks) {
        StageInstance inst = instanceRepo.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("StageInstance not found: " + instanceId));

        // Basic guard: only IN_PROGRESS or PENDING can be completed
        if (inst.getStatus() == PayrollStatus.APPROVED || inst.getStatus() == PayrollStatus.REJECTED) {
            throw new IllegalStateException("StageInstance already completed with status: " + inst.getStatus());
        }

        // If still pending, mark as in-progress first for auditing
        if (inst.getStatus() == PayrollStatus.PENDING) {
            inst.setStatus(PayrollStatus.IN_PROGRESS);
            inst.setStartedAt(Instant.now());
        }

        inst.setStatus(approve ? PayrollStatus.APPROVED : PayrollStatus.REJECTED);
        inst.setCompletedAt(Instant.now());
        inst.setExecutedById(executedById);
        inst.setRemarks(remarks);
        return instanceRepo.save(inst);
    }

    public StageInstance createNextStageInstance(UUID payrollId, String executedById, Instant dueDate) {
        // Find latest APPROVED instance (highest step)
        List<StageInstance> approved = instanceRepo.findByPayrollIdAndStatus(payrollId, PayrollStatus.APPROVED);
        int lastStep = approved.stream().map(StageInstance::getStepNumber).max(Integer::compareTo).orElse(0);

        // If there is a non-terminal current instance, use that step as base
        StageInstance current = getCurrentStageInstance(payrollId);
        if (current != null && current.getStatus() != PayrollStatus.APPROVED && current.getStatus() != PayrollStatus.REJECTED) {
            lastStep = Math.max(lastStep, current.getStepNumber());
        }

        // Determine next stage by entity + step
        StageEntity entity = approved.stream().findFirst().map(StageInstance::getEntity)
                .orElseGet(() -> Objects.requireNonNull(current, "No stage exists for payrollId").getEntity());

        Stage next = stageService.getNextStage(entity, lastStep);
        if (next == null) return null; // no more stages (workflow complete)

        StageInstance nextInst = StageInstance.builder()
                .payrollId(payrollId)
                .stageId(next.getId())
                .entity(next.getEntity())
                .stepNumber(next.getStepNumber())
                .name(next.getName())
                .status(PayrollStatus.PENDING)
                .approverId(next.getApproverId())
                .executedById(executedById) // creator of next instance
                .dueDate(dueDate)
                .createdAt(Instant.now())
                .build();

        return instanceRepo.save(nextInst);
    }
}

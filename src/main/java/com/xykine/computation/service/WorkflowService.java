package com.xykine.computation.service;

        import com.xykine.computation.entity.*;
        import com.xykine.computation.repo.StageInstanceRepository;
        import com.xykine.computation.request.UpdatePayrollStatusRequest;
        import com.xykine.computation.request.WorkflowDTOs.PAYROLL_ACTIONS;
        import com.xykine.computation.utils.AppConstants;
        import com.xykine.computation.utils.AuthUtil;
        import lombok.RequiredArgsConstructor;
        import org.springframework.stereotype.Service;
        import org.springframework.util.StringUtils;

        import java.time.Instant;
        import java.util.List;
        import java.util.UUID;

@Service @RequiredArgsConstructor
public class WorkflowService {

    private final StageService stageService;
    private final ReportPersistenceService reportPersistenceService;
    private final StageInstanceRepository instanceRepo;
    private final AdminService adminService;


    /**
     * startWorkflow(entity, companyId, userId, [payrollId])
     * - Fetch first TWO stages for (entity, companyId) by stepNumber
     * - Create two StageInstances:
     *   1) step1 → SUBMITTED
     *   2) step2 → PENDING (if exists)
     */
    public List<StageInstance> startWorkflow(StageEntity entity,
                                             String companyId,
                                             String userId,
                                             String payrollIdOrNull) {
        List<Stage> stages = stageService.getOrderedStages(entity, companyId);
        if (stages.isEmpty()) {
            throw new IllegalStateException("No stages defined for entity=" + entity + " and companyId=" + companyId);
        }

        UUID payrollId = UUID.fromString(StringUtils.hasText(payrollIdOrNull) ? payrollIdOrNull : UUID.randomUUID().toString());
        Instant now = Instant.now();

        // Stage 1 -> SUBMITTED
        Stage step1 = stages.get(0);
        StageInstance inst1 = StageInstance.builder()
                .payrollId(payrollId)
                .stageId(step1.getId())
                .entity(step1.getEntity())
                .companyId(companyId)
                .stepNumber(step1.getStepNumber())
                .name(step1.getName())
                .approverId(step1.getApproverId())
                .status(PayrollStatus.SUBMITTED)
                .actions(step1.getActions())
                .executedById(userId)
                .createdAt(now)
                .startedAt(now)              // optional: mark start at submission time
                .build();
        inst1 = instanceRepo.save(inst1);

        // Stage 2 -> PENDING (only if it exists)
        StageInstance inst2 = null;
        if (stages.size() > 1) {
            Stage step2 = stages.get(1);
            inst2 = StageInstance.builder()
                    .payrollId(payrollId)
                    .stageId(step2.getId())
                    .entity(step2.getEntity())
                    .companyId(companyId)
                    .stepNumber(step2.getStepNumber())
                    .name(step2.getName())
                    .approverId(step2.getApproverId())
                    .status(PayrollStatus.PENDING)
                    .actions(step2.getActions())
                    .executedById(userId)
                    .createdAt(now)
                    .build();
            inst2 = instanceRepo.save(inst2);
        }

        return inst2 == null ? List.of(inst1) : List.of(inst1, inst2);
    }

    /**
     * completeWorkflowStageInstance(instanceId, status)
     * - Set current instance to APPROVED or REJECTED
     * - If REJECTED: stop (return updated instance only)
     * - If APPROVED:
     *     - find next Stage by (entity, companyId, stepNumber + 1)
     *     - if exists: create new StageInstance with PENDING
     *     - if not: stop (workflow complete)
     */

    public StageInstance completeWorkflowStageInstanceAndReport(String instanceId,
                                                          PayrollStatus incomingStatus,
                                                          String executedById,
                                                          String remarks,
                                                          Instant nextDueDate, String token){
        StageInstance stageInstance  =  completeWorkflowStageInstance(instanceId, incomingStatus, executedById, remarks, nextDueDate);

        updateReportPersistenceStatus( stageInstance, token);

        return stageInstance;
    }

    public void updateReportPersistenceStatus(StageInstance stageInstance, String token){

        UpdatePayrollStatusRequest updatePayrollStatusRequest =  UpdatePayrollStatusRequest.builder()
                .reportId(stageInstance.getPayrollId())
                .companyId(stageInstance.getCompanyId())
                .status(stageInstance.getStatus()).build();

        if(stageInstance.getActions().contains(PAYROLL_ACTIONS.APPROVE_PAYROLL)){
            // TODO Call update for Approved of the payroll
            if(stageInstance.getStatus() == PayrollStatus.APPROVED){
                reportPersistenceService.updateReportStatus(updatePayrollStatusRequest);
            }
            if(stageInstance.getStatus() == PayrollStatus.REJECTED){
                reportPersistenceService.updateReportStatus(updatePayrollStatusRequest);
//                reportPersistenceService.deleteReportById(current.getPayrollId(), current.getCompanyId());
            }

        }
        if(stageInstance.getActions().contains(PAYROLL_ACTIONS.PREP_PAYMENT)){
            // TODO Call update for Approved of the payroll
            if(stageInstance.getStatus() == PayrollStatus.APPROVED){
                adminService.preparePayment(updatePayrollStatusRequest, token );
            }
            if(stageInstance.getStatus() == PayrollStatus.REJECTED){

            }
        }

        if(stageInstance.getActions().contains(PAYROLL_ACTIONS.DISBURSE)){
            // TODO Call update for Approved of the payroll
            if(stageInstance.getStatus() == PayrollStatus.APPROVED){

            }
            if(stageInstance.getStatus() == PayrollStatus.REJECTED){
                // TODO Call update for Rollback of the payroll
            }
        }
    }
    public StageInstance completeWorkflowStageInstance(String instanceId,
                                                       PayrollStatus incomingStatus,
                                                       String executedById,
                                                       String remarks,
                                                       Instant nextDueDate) {
        if (incomingStatus != PayrollStatus.APPROVED && incomingStatus != PayrollStatus.REJECTED) {
            throw new IllegalArgumentException("status must be APPROVED or REJECTED");
        }

        StageInstance current = instanceRepo.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("StageInstance not found: " + instanceId));

        System.out.println("========1==========="+ current);

        // Guard against re-completing an already terminal instance
        if (current.getStatus() == PayrollStatus.APPROVED || current.getStatus() == PayrollStatus.REJECTED) {
            return current; // idempotent return
        }

        System.out.println("========2==========="+ current);

        // Complete current
        current.setStatus(incomingStatus);
        current.setCompletedAt(Instant.now());
        if (StringUtils.hasText(executedById)) current.setExecutedById(executedById);
        if (StringUtils.hasText(remarks)) current.setRemarks(remarks);
        current = instanceRepo.save(current); // optimistic locking via @Version

        System.out.println("========3==========="+ current);
        if (incomingStatus == PayrollStatus.REJECTED) {
            return current; // stop workflow
        }
        System.out.println("========4==========="+ current);
        // Find next stage
        List<Stage> ordered = stageService.getOrderedStages(current.getEntity(), current.getCompanyId());
        StageInstance finalCurrent = current;
        Stage next = ordered.stream()
                .filter(s -> s.getStepNumber() > finalCurrent.getStepNumber())
                .findFirst()
                .orElse(null);

        if (next == null) {
            return current; // no more stages -> workflow complete
        }

        // Create next StageInstance with PENDING
        StageInstance nextInst = StageInstance.builder()
                .payrollId(current.getPayrollId())
                .stageId(next.getId())
                .entity(next.getEntity())
                .companyId(current.getCompanyId())
                .stepNumber(next.getStepNumber())
                .name(next.getName())
                .approverId(next.getApproverId())
                .status(PayrollStatus.PENDING)
                .actions(next.getActions())
                .executedById(executedById)  // actor who advanced the workflow (or system)
                .dueDate(nextDueDate)
                .createdAt(Instant.now())
                .build();

        instanceRepo.save(nextInst);


        return current;
    }
}

package com.xykine.computation.repo;

import com.xykine.computation.entity.ApprovalStatus;
import com.xykine.computation.entity.PayrollStatus;
import com.xykine.computation.entity.StageInstance;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StageInstanceRepository extends MongoRepository<StageInstance, String> {
    List<StageInstance> findByPayrollIdOrderByStepNumberAsc(String payrollId);

    // "Current" is the highest step that is not completed OR the last one created (depending on status)
    Optional<StageInstance> findFirstByPayrollIdAndStatusInOrderByStepNumberDescCreatedAtDesc(
            UUID payrollId, List<PayrollStatus> statuses);

    Optional<StageInstance> findFirstByPayrollIdOrderByStepNumberDesc(UUID payrollId);

    List<StageInstance> findByPayrollIdAndStatus(UUID payrollId, PayrollStatus status);
}

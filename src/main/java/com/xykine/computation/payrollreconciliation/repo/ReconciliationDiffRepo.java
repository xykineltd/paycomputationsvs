package com.xykine.computation.payrollreconciliation.repo;

import com.xykine.computation.payrollreconciliation.entity.ReconciliationDiff;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReconciliationDiffRepo extends MongoRepository<ReconciliationDiff, String> {
    void deleteByRunIdAndStage(String runId, String stage);
    Page<ReconciliationDiff> findByRunIdAndStage(String runId, String stage, Pageable pageable);
    Page<ReconciliationDiff> findByRunIdAndStageAndStatus(String runId, String stage, String status, Pageable pageable);
    long countByRunIdAndStage(String runId, String stage);
}

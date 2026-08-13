package com.xykine.computation.reconciliation.run;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayrollReconciliationDiffRepository extends MongoRepository<PayrollReconciliationDiff, String> {
    Page<PayrollReconciliationDiff> findByReconciliationIdAndStage(String reconciliationId, String stage, Pageable pageable);

    Page<PayrollReconciliationDiff> findByReconciliationIdAndStageAndStatus(
            String reconciliationId, String stage, String status, Pageable pageable);

    void deleteByReconciliationId(String reconciliationId);

    void deleteByReconciliationIdIn(List<String> reconciliationIds);

    void deleteByReconciliationIdAndStage(String reconciliationId, String stage);
}

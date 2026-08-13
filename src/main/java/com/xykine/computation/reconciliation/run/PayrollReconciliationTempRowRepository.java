package com.xykine.computation.reconciliation.run;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayrollReconciliationTempRowRepository extends MongoRepository<PayrollReconciliationTempRow, String> {
    List<PayrollReconciliationTempRow> findByReconciliationId(String reconciliationId);

    void deleteByReconciliationId(String reconciliationId);

    void deleteByReconciliationIdIn(List<String> reconciliationIds);

    long countByReconciliationId(String reconciliationId);
}

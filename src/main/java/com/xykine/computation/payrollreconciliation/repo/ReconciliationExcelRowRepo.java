package com.xykine.computation.payrollreconciliation.repo;

import com.xykine.computation.payrollreconciliation.entity.ReconciliationExcelRow;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ReconciliationExcelRowRepo extends MongoRepository<ReconciliationExcelRow, String> {
    List<ReconciliationExcelRow> findByRunId(String runId);
    void deleteByRunId(String runId);
    long countByRunId(String runId);
}

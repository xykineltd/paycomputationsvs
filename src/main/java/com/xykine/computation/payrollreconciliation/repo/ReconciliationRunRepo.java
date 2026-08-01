package com.xykine.computation.payrollreconciliation.repo;

import com.xykine.computation.payrollreconciliation.entity.ReconciliationRun;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReconciliationRunRepo extends MongoRepository<ReconciliationRun, String> {
}

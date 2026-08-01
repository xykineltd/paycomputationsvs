package com.xykine.computation.payrollreconciliation.repo;

import com.xykine.computation.payrollreconciliation.entity.ReconciliationMapping;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ReconciliationMappingRepo extends MongoRepository<ReconciliationMapping, String> {
    Optional<ReconciliationMapping> findByCompanyId(String companyId);
}

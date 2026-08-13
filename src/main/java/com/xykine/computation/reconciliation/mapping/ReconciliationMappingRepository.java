package com.xykine.computation.reconciliation.mapping;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReconciliationMappingRepository extends MongoRepository<ReconciliationMapping, String> {
    Optional<ReconciliationMapping> findByCompanyIdAndDeletedAtIsNull(String companyId);

    Optional<ReconciliationMapping> findByIdAndDeletedAtIsNull(String id);

    boolean existsByCompanyIdAndDeletedAtIsNull(String companyId);
}

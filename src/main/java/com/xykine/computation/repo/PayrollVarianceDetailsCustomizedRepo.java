package com.xykine.computation.repo;


import com.xykine.computation.entity.PayrollVarianceDetailsCustomized;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface PayrollVarianceDetailsCustomizedRepo extends MongoRepository<PayrollVarianceDetailsCustomized, UUID> {
    Optional<PayrollVarianceDetailsCustomized> findById(UUID id);
}

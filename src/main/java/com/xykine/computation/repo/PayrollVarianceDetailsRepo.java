package com.xykine.computation.repo;

import com.xykine.computation.entity.PayrollVarianceDetails;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface PayrollVarianceDetailsRepo extends MongoRepository<PayrollVarianceDetails, UUID> {
    Optional<PayrollVarianceDetails> findById(UUID id);
}

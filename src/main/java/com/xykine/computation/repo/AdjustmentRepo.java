package com.xykine.computation.repo;

import com.xykine.computation.domain.AdjustmentStatus;
import com.xykine.computation.entity.Adjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AdjustmentRepo extends MongoRepository<Adjustment, String> {
    Page<Adjustment> findByLoanId(String loanId, Pageable pageable);
}
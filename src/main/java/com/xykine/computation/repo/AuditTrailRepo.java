package com.xykine.computation.repo;

import com.xykine.computation.entity.AuditTrail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditTrailRepo extends MongoRepository<AuditTrail,String> {

    Page<AuditTrail> findAuditTrailByUserId(String userId, Pageable pageable);
    Page<AuditTrail> findAllByOrderByDateTimeDesc(Pageable pageable);
}
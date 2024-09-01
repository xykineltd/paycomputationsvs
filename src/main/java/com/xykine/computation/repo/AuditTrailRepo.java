package com.xykine.computation.repo;

import com.xykine.computation.entity.AuditTrail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;

public interface AuditTrailRepo extends MongoRepository<AuditTrail,String> {

//    Page<AuditTrail> findAuditTrailByUserId(String userId, Pageable pageable);
//    Page<AuditTrail> findAuditTrailByUserIdContaining(String userId, Pageable pageable);
    Page<AuditTrail> findByUserIdContainingAndDateTimeBetween(
            String userId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable);
    Page<AuditTrail> findByCompanyIdOrderByDateTimeDesc(String companyId, Pageable pageable);
}

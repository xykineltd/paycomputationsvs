package com.xykine.computation.repo;

import com.xykine.computation.entity.AuditTrail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditTrailRepo extends MongoRepository<AuditTrail,String> {

//    Page<AuditTrail> findAuditTrailByUserId(String userId, Pageable pageable);
//    Page<AuditTrail> findAuditTrailByUserIdContaining(String userId, Pageable pageable);
    Page<AuditTrail> findByEmployeeIdContainingIgnoreCaseAndDateTimeBetweenAndCompanyId(
            String userId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String companyId,
            Pageable pageable
    );
    Page<AuditTrail> findByCompanyIdOrderByDateTimeDesc(String companyId, Pageable pageable);
}

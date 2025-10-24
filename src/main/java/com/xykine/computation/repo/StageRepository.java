package com.xykine.computation.repo;

import com.xykine.computation.entity.Stage;
import com.xykine.computation.entity.StageEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface StageRepository extends MongoRepository<Stage, String> {
    List<Stage> findByEntityOrderByStepNumberAsc(StageEntity entity);
    List<Stage> findByEntityAndCompanyIdOrderByStepNumberAsc(StageEntity entity, String companyId);

    boolean existsByEntityAndCompanyIdAndStepNumber(StageEntity entity, String companyId, Integer stepNumber);

    Optional<Stage> findByEntityAndCompanyIdAndStepNumber(StageEntity entity, String companyId, Integer stepNumber);
}

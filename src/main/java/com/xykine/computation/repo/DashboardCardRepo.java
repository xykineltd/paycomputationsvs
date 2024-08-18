package com.xykine.computation.repo;

import com.xykine.computation.entity.DashboardCard;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface DashboardCardRepo extends MongoRepository<DashboardCard,String> {
    Optional<DashboardCard> findByTableMarker(String id);
}

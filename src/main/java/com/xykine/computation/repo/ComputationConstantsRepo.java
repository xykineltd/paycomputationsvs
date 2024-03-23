package com.xykine.computation.repo;


import com.xykine.computation.entity.ComputationConstants;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ComputationConstantsRepo extends MongoRepository<ComputationConstants,String> {
    @Cacheable(value = "computationConstants")
    List<ComputationConstants> findAllByOrderById();
}

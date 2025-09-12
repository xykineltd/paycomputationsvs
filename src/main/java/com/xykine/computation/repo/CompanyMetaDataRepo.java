package com.xykine.computation.repo;

import com.xykine.computation.entity.CompanyMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CompanyMetaDataRepo extends MongoRepository<CompanyMetadata,String> {
    Optional<CompanyMetadata> findAByCompanyId(String companyId);
}

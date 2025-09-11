package com.xykine.computation.repo;


import com.xykine.computation.entity.EmployeeMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeMetadataRepo extends MongoRepository<EmployeeMetadata,String> {
   Optional<EmployeeMetadata> findByEmployeeId(String employeeId);
   List<EmployeeMetadata> findAllByCompanyId(String companyId);
}

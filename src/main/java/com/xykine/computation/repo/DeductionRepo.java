package com.xykine.computation.repo;

import com.xykine.computation.entity.Deductions;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DeductionRepo extends MongoRepository<Deductions,String> {
    List<Deductions> findDeductionByEmployeeId (Long employeeId);
}

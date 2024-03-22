package com.xykine.computation.repo;

import com.xykine.computation.entity.PensionFund;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PensionFundRepo extends MongoRepository<PensionFund,String> {
    PensionFund findPensionFundByEmployeeId(Long employeeId);
}

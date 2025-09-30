package com.xykine.computation.repo;

import com.xykine.computation.domain.LoanStatus;
import com.xykine.computation.entity.Loan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface LoanRepo extends MongoRepository<Loan, String>{
    Optional<Loan> findOneByCompanyIdAndEmployeeIdAndDescriptionAndActiveIsTrue(String companyId, String employeeId, String description);
}
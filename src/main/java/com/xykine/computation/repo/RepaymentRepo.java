package com.xykine.computation.repo;

import com.xykine.computation.entity.Repayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RepaymentRepo extends MongoRepository<Repayment, String> {
    Page<Repayment> findByLoanId(String loanId, Pageable pageable);
    Page<Repayment> findByCompanyIdAndEmployeeId(String companyId, String employeeId, Pageable pageable);
}

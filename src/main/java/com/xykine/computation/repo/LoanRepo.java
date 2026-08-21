package com.xykine.computation.repo;

import com.xykine.computation.domain.LoanStatus;
import com.xykine.computation.entity.Loan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoanRepo extends MongoRepository<Loan, String>{
    Optional<Loan> findOneByCompanyIdAndEmployeeIdAndDescriptionAndActiveIsTrue(String companyId, String employeeId, String description);

    @Query("""
    {
      'companyId': ?0,
      'active': true,
      'status': 'APPROVED',
      $or: [
        { 'endDate': null },
        { 'endDate': { $gte: ?1 } }
      ]
    }
    """)
    List<Loan> findActiveApprovedNonExpiredLoans(String companyId, LocalDate today);

}
package com.xykine.computation.service;

import com.xykine.computation.entity.Loan;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.xykine.computation.entity.Repayment;
import com.xykine.computation.entity.Adjustment;
import com.xykine.computation.dto.LoanFilter;

import com.xykine.computation.request.UpdateLoanRequest;
import com.xykine.computation.request.CreateLoanRequest;
import com.xykine.computation.request.AdjustLoanRequest;
import com.xykine.computation.request.RepaymentRequest;

import java.time.LocalDate;

public interface LoanService {
    Loan createLoan(CreateLoanRequest req);
    Page<Repayment> getEmployeeRepayments(String companyId, String employeeId, Pageable pageable);
    Page<Repayment> getLoanRepayments(String loanId, Pageable pageable);
    Loan updateLoan(String loanId, UpdateLoanRequest req);
    Page<Loan> getLoans(LoanFilter filter, LocalDate startDate, Pageable pageable);
    Adjustment createAdjustment(String loanId, AdjustLoanRequest req);
    Page<Adjustment> getAdjustmentsForLoan(String loanId, Pageable pageable);
    Repayment recordRepayment(String loanId, RepaymentRequest req);
    Loan getEmployeeActiveLoan(String companyId, String employeeId, String description);
}

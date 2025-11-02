package com.xykine.computation.controller;


import com.xykine.computation.domain.LoanStatus;
import com.xykine.computation.dto.LoanFilter;
import com.xykine.computation.entity.Adjustment;
import com.xykine.computation.entity.Loan;
import com.xykine.computation.entity.Repayment;
import com.xykine.computation.request.AdjustLoanRequest;
import com.xykine.computation.request.CreateLoanRequest;
import com.xykine.computation.request.RepaymentRequest;
import com.xykine.computation.request.UpdateLoanRequest;
import com.xykine.computation.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/compute/")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    // Create loan (optional but handy)
    @PostMapping("/loans")
    public Loan createLoan(@Valid @RequestBody CreateLoanRequest req) {
        return loanService.createLoan(req);
    }

    // allLoan: by companyId, optional filters: status, employeeId, createdDate range — Paginated
    @GetMapping("/loans")
    public Page<Loan> getAllLoans(
            @RequestParam String companyId,
            @RequestParam(required = false) LoanStatus status,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        String[] sortParts = sort.split(",", 2);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortParts.length>1?sortParts[1]:"desc"), sortParts[0]));
        LoanFilter f = new LoanFilter();
        f.setCompanyId(companyId);
        f.setStatus(status);
        f.setEmployeeId(employeeId);
        f.setCreatedFrom(createdFrom);
        f.setCreatedTo(createdTo);
        return loanService.getLoans(f, pageable);
    }

    // employeeLoanDetail: show ALL loan repayments for an employee (Paginated across loans)
    @GetMapping("/loans/employee/{employeeId}/repayments")
    public Page<Repayment> getEmployeeLoanDetail(
            @PathVariable String employeeId,
            @RequestParam String companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "paidAt,desc") String sort
    ) {
        String[] sortParts = sort.split(",", 2);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortParts.length>1?sortParts[1]:"desc"), sortParts[0]));
        return loanService.getEmployeeRepayments(companyId, employeeId, pageable);
    }

    // Also handy: repayments for a specific loan (Paginated)
    @GetMapping("/loans/{loanId}/repayments")
    public Page<Repayment> getLoanRepayments(
            @PathVariable String loanId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "paidAt,desc") String sort
    ) {
        String[] sortParts = sort.split(",", 2);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortParts.length>1?sortParts[1]:"desc"), sortParts[0]));
        return loanService.getLoanRepayments(loanId, pageable);
    }

    // updateLoan: status, amount(principal), active
    @PatchMapping("/loans/{loanId}")
    public Loan updateLoan(
            @PathVariable String loanId,
            @Valid @RequestBody UpdateLoanRequest req
    ) {
        return loanService.updateLoan(loanId, req);
    }

    // Record a repayment (not listed but essential)
    @PostMapping("/loans/{loanId}/repayments")
    public Repayment addRepayment(
            @PathVariable String loanId,
            @Valid @RequestBody RepaymentRequest req
    ) {
        return loanService.recordRepayment(loanId, req);
    }

    // adjustLoan
    @PostMapping("/loans/{loanId}/adjustments")
    public Adjustment adjustLoan(
            @PathVariable String loanId,
            @Valid @RequestBody AdjustLoanRequest req
    ) {
        return loanService.createAdjustment(loanId, req);
    }

    // allAdjustmentsForLoan (Paginated)
    @GetMapping("/loans/{loanId}/adjustments")
    public Page<Adjustment> getAdjustments(
            @PathVariable String loanId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        String[] sortParts = sort.split(",", 2);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortParts.length>1?sortParts[1]:"desc"), sortParts[0]));
        return loanService.getAdjustmentsForLoan(loanId, pageable);
    }
}

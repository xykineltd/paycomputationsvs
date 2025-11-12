package com.xykine.computation.service;

import com.xykine.computation.domain.AdjustmentStatus;
import com.xykine.computation.domain.AdjustmentType;
import com.xykine.computation.domain.LoanStatus;
import com.xykine.computation.dto.LoanFilter;
import com.xykine.computation.entity.Adjustment;
import com.xykine.computation.entity.Loan;
import com.xykine.computation.entity.Repayment;
import com.xykine.computation.repo.AdjustmentRepo;
import com.xykine.computation.repo.LoanRepo;
import com.xykine.computation.repo.RepaymentRepo;
import com.xykine.computation.request.AdjustLoanRequest;
import com.xykine.computation.request.CreateLoanRequest;
import com.xykine.computation.request.RepaymentRequest;
import com.xykine.computation.request.UpdateLoanRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoanServiceImpl implements LoanService {

    private final LoanRepo loanRepo;
    private final RepaymentRepo repaymentRepo;
    private final AdjustmentRepo adjustmentRepo;
    private final MongoTemplate mongoTemplate;

    protected static final Logger LOGGER = LoggerFactory.getLogger(LoanServiceImpl.class);

    @Override
    public Loan createLoan(CreateLoanRequest req) {
        Loan loan = Loan.builder()
                .companyId(req.getCompanyId())
                .employeeId(req.getEmployeeId())
                .principalAmount(req.getPrincipalAmount())
                .outstandingAmount(req.getPrincipalAmount())
                .scheduledRepaymentAmount(req.getScheduledRepaymentAmount())
                .description(req.getDescription())
                .status(LoanStatus.PENDING)
                .active(true)
                .build();
        return loanRepo.save(loan);
    }

    @Override
    public Page<Loan> getLoans(LoanFilter filter, Pageable pageable) {
        if (filter.getCompanyId() == null) {
            throw new IllegalArgumentException("CompanyId must be set");
        }

        Criteria criteria = Criteria.where("companyId").is(filter.getCompanyId());

        if (filter.getEmployeeId() != null) {
            criteria.and("employeeId").is(filter.getEmployeeId());
        }

        if (filter.getStatus() != null) {
            criteria.and("status").is(filter.getStatus());
        }

        Query query = new Query(criteria).with(pageable);

        List<Loan> loans = mongoTemplate.find(query, Loan.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Loan.class);

        LOGGER.debug(" ====> getLoans {} ", loans);

        return new PageImpl<>(loans, pageable, total);
    }

    @Override
    public Page<Repayment> getEmployeeRepayments(String companyId, String employeeId, Pageable pageable) {
        return repaymentRepo.findByCompanyIdAndEmployeeId(companyId, employeeId, pageable);
    }

    @Override
    public Page<Repayment> getLoanRepayments(String loanId, Pageable pageable) {
        return repaymentRepo.findByLoanId(loanId, pageable);
    }

    @Override
    @Transactional
    public Loan updateLoan(String loanId, UpdateLoanRequest req) {
        Loan loan = loanRepo.findById(loanId).orElseThrow(() -> new IllegalArgumentException("Loan not found"));

        if (req.getPrincipalAmount()!=null) {
            BigDecimal delta = req.getPrincipalAmount().subtract(loan.getPrincipalAmount());
            loan.setPrincipalAmount(req.getPrincipalAmount());
            loan.setOutstandingAmount(loan.getOutstandingAmount().add(delta));
        }
        if (req.getStatus()!=null) {
            loan.setStatus(req.getStatus());
            if (req.getStatus()==LoanStatus.APPROVED && loan.getApprovedAt()==null) {
                loan.setApprovedAt(Instant.now());
            }
        }
        if (req.getActive()!=null) loan.setActive(req.getActive());

        return loanRepo.save(loan);
    }


    @Override
    @Transactional
    public Adjustment createAdjustment(String loanId, AdjustLoanRequest req) {
        Loan loan = loanRepo.findById(loanId).orElseThrow(() -> new IllegalArgumentException("Loan not found"));

        Adjustment adj = Adjustment.builder()
                .loanId(loan.getId())
                .companyId(loan.getCompanyId())
                .employeeId(loan.getEmployeeId())
                .type(req.getType())
                .amount(req.getAmount())
                .reason(req.getReason())
                .status(req.isApproveNow()? AdjustmentStatus.APPROVED : AdjustmentStatus.PENDING)
                .approvedBy(req.isApproveNow()? req.getApprovedBy() : null)
                .approvedAt(req.isApproveNow()? Instant.now() : null)
                .build();

        if (req.isApproveNow()) {
            applyAdjustmentToLoan(loan, req.getType(), req.getAmount());
            loanRepo.save(loan);
        }

        return adjustmentRepo.save(adj);
    }

    private void applyAdjustmentToLoan(Loan loan, AdjustmentType type, BigDecimal amount) {
        if (type==AdjustmentType.INCREASE) {
            loan.setPrincipalAmount(loan.getPrincipalAmount().add(amount));
            loan.setOutstandingAmount(loan.getOutstandingAmount().add(amount));
        } else {
            loan.setPrincipalAmount(loan.getPrincipalAmount().subtract(amount).max(BigDecimal.ZERO));
            loan.setOutstandingAmount(loan.getOutstandingAmount().subtract(amount).max(BigDecimal.ZERO));
        }
    }

    @Override
    public Page<Adjustment> getAdjustmentsForLoan(String loanId, Pageable pageable) {
        return adjustmentRepo.findByLoanId(loanId, pageable);
    }

    @Override
    @Transactional
    public Repayment recordRepayment(String loanId, RepaymentRequest req) {
        Loan loan = loanRepo.findById(loanId).orElseThrow(() -> new IllegalArgumentException("Loan not found"));
        boolean isFullyPaid = false;
        BigDecimal delta = loan.getOutstandingAmount().subtract(req.getAmount());
        delta = delta.compareTo(BigDecimal.ZERO) >= 0 ? delta : BigDecimal.ZERO;
        if (delta.compareTo(BigDecimal.ZERO)==0) {
            req.setAmount(loan.getOutstandingAmount());
            isFullyPaid = true;
        }

        Repayment r = Repayment.builder()
                .loanId(loan.getId())
                .companyId(loan.getCompanyId())
                .employeeId(loan.getEmployeeId())
                .amount(req.getAmount())
                .paidAt(req.getPaidAt()!=null ? req.getPaidAt() : Instant.now())
                .reference(req.getReference())
                .build();
        Repayment saved = repaymentRepo.save(r);

        if (isFullyPaid) {
            loan.setOutstandingAmount(BigDecimal.ZERO);
            loan.setActive(false);
        } else{
            loan.setOutstandingAmount(loan.getOutstandingAmount().subtract(req.getAmount()).max(BigDecimal.ZERO));
        }
        loanRepo.save(loan);
        return saved;
    }

    @Override
    public Loan getEmployeeActiveLoan(String companyId, String employeeId, String description) {
        return loanRepo.findOneByCompanyIdAndEmployeeIdAndDescriptionAndActiveIsTrue(companyId, employeeId, description).orElse(null);
    }
}
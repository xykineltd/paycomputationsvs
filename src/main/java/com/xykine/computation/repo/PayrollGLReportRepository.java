package com.xykine.computation.repo;

import com.xykine.computation.dto.GLReportStatus;
import com.xykine.computation.entity.PayrollGLReport;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollGLReportRepository
        extends MongoRepository<PayrollGLReport, String> {

    Optional<PayrollGLReport> findByPayrollId(String payrollId);

    Optional<PayrollGLReport> findByPayrollPeriod(LocalDate payrollPeriod);

    Optional<PayrollGLReport> findByPayrollIdAndPayrollPeriod(
            String payrollId,
            LocalDate payrollPeriod
    );

    List<PayrollGLReport> findByStatus(GLReportStatus status);

    List<PayrollGLReport> findByPayrollPeriodBetween(
            LocalDate startPeriod,
            LocalDate endPeriod
    );

    boolean existsByPayrollId(String payrollId);

    boolean existsByPayrollIdAndPayrollPeriod(
            String payrollId,
            LocalDate payrollPeriod
    );

    Optional<PayrollGLReport> findFirstByPayrollIdOrderByGeneratedDesc(
            String payrollId
    );
}

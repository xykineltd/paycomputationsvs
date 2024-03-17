package com.xykine.computation.repo;

import com.xykine.computation.entity.PayrollReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface PayrollReportRepo extends JpaRepository<PayrollReport, Long> {
    PayrollReport findPayrollReportByStartDate(LocalDate startDate);
}

package com.xykine.computation.repo;

import com.xykine.computation.entity.PayrollReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface PayrollReportRepo extends JpaRepository<PayrollReport, Long> {
    PayrollReport findPayrollReportByStartDate(LocalDate startDate);
    List<PayrollReport> findAllByOrderByCreatedDateDesc();

     void deletePayrollReportByStartDate(LocalDate startDate);
}

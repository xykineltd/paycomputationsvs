package com.xykine.computation.repo;

import com.xykine.computation.entity.PayrollReport;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface PayrollReportRepo extends MongoRepository<PayrollReport,String> {
    PayrollReport findPayrollReportByStartDateAndPayrollSimulation(LocalDate startDate, boolean simulation);
    List<PayrollReport> findAllByOrderByCreatedDateAsc();
     void deletePayrollReportByStartDate(LocalDate startDate);
     void deletePayrollReportsByPayrollSimulation(Boolean simulation);
}

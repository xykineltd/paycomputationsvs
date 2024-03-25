package com.xykine.computation.repo;

import com.xykine.computation.entity.PayrollReport;
import com.xykine.computation.entity.PayrollReportSummary;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PayrollReportSummaryRepo extends MongoRepository<PayrollReportSummary, UUID> {

    PayrollReportSummary findPayrollReportSummaryByStartDateAndPayrollSimulation(LocalDate startDate, boolean simulation);
    List<PayrollReportSummary> findAllByOrderByCreatedDateAsc();
    void deletePayrollReportSummaryByStartDate(LocalDate startDate);
    void deletePPayrollReportSummaryByPayrollSimulation(Boolean simulation);
}

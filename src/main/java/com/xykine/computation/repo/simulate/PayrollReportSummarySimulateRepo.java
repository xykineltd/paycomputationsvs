package com.xykine.computation.repo.simulate;

import com.xykine.computation.entity.simulate.PayrollReportSummarySimulate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PayrollReportSummarySimulateRepo extends MongoRepository<PayrollReportSummarySimulate, UUID> {

    PayrollReportSummarySimulate findPayrollReportSummaryByStartDate(LocalDate startDate);
    List<PayrollReportSummarySimulate> findAllByCompanyIdOrderByCreatedDateAsc(String companyId);
    void deleteAllByStartDate(LocalDate startDate);
    void deletePayrollReportSummaryByPayrollSimulation(Boolean simulation);
}

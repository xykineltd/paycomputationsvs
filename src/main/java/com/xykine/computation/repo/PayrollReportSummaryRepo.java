package com.xykine.computation.repo;

import com.xykine.computation.entity.PayrollReportSummary;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PayrollReportSummaryRepo extends MongoRepository<PayrollReportSummary, UUID> {

    PayrollReportSummary findPayrollReportSummaryByStartDateAndCompanyIdAndPayrollSimulation(LocalDate startDate, String companyId, boolean simulation);
    PayrollReportSummary findPayrollReportSummaryByCompanyIdAndOffCycleId(String companyId, String offCycleId);
    List<PayrollReportSummary> findAllByCompanyIdOrderByCreatedDateAsc(String companyId);
    void deletePayrollReportSummaryByStartDateAndCompanyId(LocalDate startDate, String companyId);
    PayrollReportSummary findPayrollReportSummaryById(UUID id);
    PayrollReportSummary findPayrollReportSummaryByPayrollApprovedAndStartDateAndCompanyId(boolean payrollApproved, LocalDate startDate, String companyId);
    void deletePayrollReportSummaryByStartDate(Boolean simulation);
}

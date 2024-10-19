package com.xykine.computation.repo;

import com.xykine.computation.entity.PayrollReportSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PayrollReportSummaryRepo extends MongoRepository<PayrollReportSummary, UUID> {

    PayrollReportSummary findPayrollReportSummaryByStartDateAndCompanyIdAndPayrollSimulation(String startDate, String companyId, boolean simulation);
    List<PayrollReportSummary> findAllByCompanyIdAndPayrollSimulationAndOffCycle(String companyId, boolean simulation, boolean offCycle);
    Page<PayrollReportSummary> findAllByCompanyIdAndStartDateBetweenAndOffCycle(String companyId, String startDateLow, String startDateHigh, boolean offCycle, Pageable pageable);
    Page<PayrollReportSummary> findAllByCompanyIdAndStartDateBetween(String companyId, String startDateLow, String startDateHigh, Pageable pageable);
    PayrollReportSummary findPayrollReportSummaryByCompanyIdAndOffCycleId(String companyId, String offCycleId);
    List<PayrollReportSummary> findAllByCompanyIdOrderByCreatedDateAsc(String companyId);
    void deletePayrollReportSummaryByStartDateAndCompanyId(String startDate, String companyId);
    void deletePayrollReportSummaryByOffCycleIdAndCompanyId(String offCycleId, String companyId);
    PayrollReportSummary findPayrollReportSummaryById(UUID id);
    PayrollReportSummary findPayrollReportSummaryByPayrollApprovedAndStartDateAndCompanyId(boolean payrollApproved, String startDate, String companyId);
    void deletePayrollReportSummaryByStartDate(Boolean simulation);
    PayrollReportSummary findPayrollReportSummaryByStartDateAndCompanyId(String startDate, String companyID);

}

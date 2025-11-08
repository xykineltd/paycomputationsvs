package com.xykine.computation.repo;

import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.entity.PayrollStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollReportSummaryRepo extends MongoRepository<PayrollReportSummary, UUID> {

    PayrollReportSummary findPayrollReportSummaryByStartDateAndCompanyIdAndPayrollSimulation(String startDate, String companyId, boolean simulation);
    Page<PayrollReportSummary> findPayrollReportSummaryByCompanyIdAndPayrollSimulationOrderByCreatedDateDesc(String companyId, boolean simulation, Pageable pageable);
    Page<PayrollReportSummary> findPayrollReportSummaryByCompanyIdOrderByCreatedDateDesc(String companyId, Pageable pageable);
    PayrollReportSummary findPayrollReportSummaryByStartDateAndCompanyIdAndOffCycleIdAndPayrollSimulation(String startDate, String companyId, String offCycleId, boolean simulation);
    List<PayrollReportSummary> findAllByCompanyIdAndPayrollSimulationAndOffCycle(String companyId, boolean simulation, boolean offCycle);
    Page<PayrollReportSummary> findAllByCompanyIdAndStartDateBetweenAndOffCycle(String companyId, String startDateLow, String startDateHigh, boolean offCycle, Pageable pageable);
    Page<PayrollReportSummary> findAllByCompanyIdAndStartDateBetween(String companyId, String startDateLow, String startDateHigh, Pageable pageable);
    PayrollReportSummary findPayrollReportSummaryByCompanyIdAndOffCycleId(String companyId, String offCycleId);
    Page<PayrollReportSummary> findAllByCompanyIdOrderByCreatedDateAsc(String companyId, Pageable pageable);
//    List<PayrollReportSummary> findAllByPayrollCompletedAndPayrollApprovedAndCompanyIdOrderByCreatedDateAsc(boolean completed, boolean approved, String companyId);
    List<PayrollReportSummary> findAllByCompanyIdOrderByCreatedDateAsc(String companyId);
    List<PayrollReportSummary> findAllByPayrollStatusAndCompanyIdOrderByCreatedDateAsc(PayrollStatus status, String companyId);
    void deletePayrollReportSummaryByStartDateAndCompanyId(String startDate, String companyId);
//    void deletePayrollReportSummaryByIdAndCompanyId(String startDate, String companyId);
    void deleteAllByIdAndCompanyId(String id, String companyId);
    void deletePayrollReportSummaryByOffCycleIdAndCompanyId(String offCycleId, String companyId);
    PayrollReportSummary findPayrollReportSummaryById(UUID id);
    List<PayrollReportSummary> findPayrollReportSummaryByCompanyId(String companyId);
    List<PayrollReportSummary> findPayrollReportSummaryByIdInAndCompanyId(List<String> summaryIds, String companyId);
    PayrollReportSummary findPayrollReportSummaryByPayrollStatusAndStartDateAndCompanyId(PayrollStatus payrollStatus, String startDate, String companyId);
    void deletePayrollReportSummaryByStartDate(Boolean simulation);
    PayrollReportSummary findPayrollReportSummaryByStartDateAndCompanyId(String startDate, String companyID);

    PayrollReportSummary findPayrollReportSummaryByStartDateAndCompanyIdAndOffCycle(String startDate, String companyID, Boolean offCycle);
    Optional<PayrollReportSummary> findTopByCompanyIdAndPayrollStatusAndOffCycleFalseOrderByEndDateDesc(String companyId, PayrollStatus payrollStatus);
    Optional<PayrollReportSummary> findPayrollReportSummaryByIdAndCompanyId(UUID id, String companyId);
    PayrollReportSummary findPayrollReportSummaryByIdAndCompanyIdAndPayrollSimulation(UUID id, String companyId, boolean simulation);
    void deletePayrollReportSummaryByIdAndCompanyId(UUID id, String companyId);

}

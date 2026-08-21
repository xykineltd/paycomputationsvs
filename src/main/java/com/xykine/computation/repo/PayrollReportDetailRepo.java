package com.xykine.computation.repo;

import com.xykine.computation.entity.PayrollReportDetail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface PayrollReportDetailRepo extends MongoRepository<PayrollReportDetail, String> {
    void deletePayrollReportsByStartDate(LocalDate startDate);
    void deleteAllByStartDateAndCompanyId(LocalDate startDate, String companyId);
    void deleteAllBySummaryIdAndCompanyId(String summaryId, String companyId);
    void deleteAllByOffCycleIdAndCompanyId(String offCycleId, String companyId);
    void deleteAllBySummaryId(String summaryId);
    PayrollReportDetail findPayrollReportDetailByCompanyIdAndEmployeeIdAndStartDateAndEndDateAndSummaryId(String companyId, String employeeId, String startDate, String endDate, String summaryId);
    List<PayrollReportDetail> findPayrollReportDetailBySummaryId(String id);
    List<PayrollReportDetail> findPayrollReportDetailByEmployeeIdInAndCompanyIdAndSummaryId(List<String> ids, String companyId, String summaryId);
    Page<PayrollReportDetail> findPayrollReportDetailByCompanyIdAndEmployeeIdAndStartDateBetweenAndOffCycle(String companyId, String employeeID, String startDateLow, String startDateHigh, boolean offCycle, Pageable pageable);
    Page<PayrollReportDetail> findPayrollReportDetailBySummaryIdAndCompanyId(String summaryId, String companyId, Pageable pageable);
    Page<PayrollReportDetail> findPayrollReportDetailBySummaryIdAndCompanyIdAndFullNameContainingIgnoreCase(String summaryId, String companyId, String fullName, Pageable pageable);
    List<PayrollReportDetail> findPayrollReportDetailByEmployeeIdAndCompanyId(String employeeId,String companyId);
    Page<PayrollReportDetail> findPayrollReportDetailByEmployeeIdAndCompanyId(String employeeId,String companyId, Pageable pageable);
    Page<PayrollReportDetail> findPayrollReportDetailByCompanyIdAndEmployeeId(String companyId,String employeeId, Pageable pageable);
    Page<PayrollReportDetail> findPayrollReportDetailByCompanyIdAndEmployeeIdInAndSummaryId(String companyId,List<String> employeeIdList, String summaryId, Pageable pageable);
    Page<PayrollReportDetail> findPayrollReportDetailByCompanyIdAndSummaryId(String companyId, String summaryId, Pageable pageable);
    List<PayrollReportDetail> findPayrollReportDetailByCompanyIdAndSummaryId(String companyId, String summaryId);
    @Query(value="{ 'companyId' : ?0, 'startDate' : ?1 }", fields="{ 'employeeId' : 1 }")
    List<PayrollReportDetail> findDistinctEmployeeIdsByCompanyIdAndStartDate(String companyId, LocalDate startDate);
    List<PayrollReportDetail> findByCompanyId(String companyId);
    long countByCompanyId(String companyId);
    long countBySummaryId(String summaryId);
}


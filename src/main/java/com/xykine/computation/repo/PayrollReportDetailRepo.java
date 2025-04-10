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
    void deleteAllByOffCycleIdAndCompanyId(String offCycleId, String companyId);
    PayrollReportDetail findPayrollReportByStartDate(LocalDate startDate);
    List<PayrollReportDetail> findPayrollReportDetailBySummaryId(String id);
    Page<PayrollReportDetail> findPayrollReportDetailByCompanyIdAndEmployeeIdAndStartDateBetweenAndOffCycle(String companyId, String employeeID, String startDateLow, String startDateHigh, boolean offCycle, Pageable pageable);
//    Page<PayrollReportDetail> findPayrollReportDetailByEmployeeIdAndCompanyId(String employeeId, String companyId, Pageable pageable);
    Page<PayrollReportDetail> findPayrollReportDetailBySummaryIdAndCompanyId(String summaryId, String companyId, Pageable pageable);
    Page<PayrollReportDetail> findPayrollReportDetailBySummaryIdAndCompanyIdAndFullNameContainingIgnoreCase(String summaryId, String companyId, String fullName, Pageable pageable);

    //TODO fix the payrollApproved column for payrollReportDetails and then use this method
    //    List<PayrollReportDetail> findPayrollReportDetailByEmployeeIdAndCompanyIdAndPayrollApproved(String employeeId,
//                                                                                                String companyId, boolean payrollApproved);
    List<PayrollReportDetail> findPayrollReportDetailByEmployeeIdAndCompanyId(String employeeId,String companyId);

    Page<PayrollReportDetail> findPayrollReportDetailByEmployeeIdAndCompanyId(String employeeId,String companyId, Pageable pageable);

//    @Query(value="{ 'companyId' : ?0, 'startDate' : ?1 }", fields="{ 'employeeId' : 1 }")
//    List<PayrollReportDetail> findDistinctEmployeeIdByCompanyIdAndStartDate(String companyId, LocalDate startDate);

    @Query(value="{ 'companyId' : ?0, 'startDate' : ?1 }", fields="{ 'employeeId' : 1 }")
    List<PayrollReportDetail> findDistinctEmployeeIdsByCompanyIdAndStartDate(String companyId, LocalDate startDate);

    List<PayrollReportDetail> findByCompanyId(String companyId);

    long countByCompanyId(String companyId);
    long countBySummaryId(String summaryId);
}


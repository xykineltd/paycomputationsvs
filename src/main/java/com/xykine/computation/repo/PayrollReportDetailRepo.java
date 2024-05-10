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
    void deletePayrollReportDetailByStartDateAndCompanyId(LocalDate startDate, String companyId);
    PayrollReportDetail findPayrollReportByStartDate(LocalDate startDate);
    Page<PayrollReportDetail> findPayrollReportDetailById(String id, Pageable pageable);
    Page<PayrollReportDetail> findPayrollReportDetailBySummaryIdAndCompanyId(String summaryId, String companyId, Pageable pageable);

//    @Query(value="{ 'companyId' : ?0, 'startDate' : ?1 }", fields="{ 'employeeId' : 1 }")
//    List<PayrollReportDetail> findDistinctEmployeeIdByCompanyIdAndStartDate(String companyId, LocalDate startDate);

    @Query(value="{ 'companyId' : ?0, 'startDate' : ?1 }", fields="{ 'employeeId' : 1 }")
    List<PayrollReportDetail> findDistinctEmployeeIdsByCompanyIdAndStartDate(String companyId, LocalDate startDate);

    List<PayrollReportDetail> findByCompanyId(String companyId);

    long countByCompanyId(String companyId);

}


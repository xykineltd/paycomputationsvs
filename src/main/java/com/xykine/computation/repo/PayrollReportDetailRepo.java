package com.xykine.computation.repo;

import com.xykine.computation.entity.PayrollReportDetail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;

public interface PayrollReportDetailRepo extends MongoRepository<PayrollReportDetail, String> {
    void deletePayrollReportsByStartDate(LocalDate startDate);
    void deletePayrollReportDetailByStartDate(LocalDate startDate);
    PayrollReportDetail findPayrollReportByStartDate(LocalDate startDate);
    Page<PayrollReportDetail> findPayrollReportDetailById(String id, Pageable pageable);
    Page<PayrollReportDetail> findPayrollReportDetailBySummaryId(String summaryId, Pageable pageable);
}


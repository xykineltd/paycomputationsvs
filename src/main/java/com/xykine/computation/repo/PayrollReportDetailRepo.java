package com.xykine.computation.repo;

import com.xykine.computation.entity.PayrollReport;
import com.xykine.computation.entity.PayrollReportDetail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;

public interface PayrollReportDetailRepo extends MongoRepository<PayrollReportDetail, String> {
    void deletePayrollReportsByPayrollSimulation(Boolean simulation);
    Page<PayrollReportDetail> findPayrollReportDetailById(String id, Pageable pageable);
    Page<PayrollReportDetail> findPayrollReportDetailBySummaryId(String summaryId, Pageable pageable);
}


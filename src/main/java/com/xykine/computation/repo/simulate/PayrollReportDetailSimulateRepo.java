package com.xykine.computation.repo.simulate;

import com.xykine.computation.entity.PayrollReportDetail;
import com.xykine.computation.entity.simulate.PayrollReportDetailSimulate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;


public interface PayrollReportDetailSimulateRepo extends MongoRepository<PayrollReportDetailSimulate, String> {
    void deletePayrollReportsByPayrollSimulation(Boolean simulation);
    void deleteAllByStartDate(String startDate);
    Page<PayrollReportDetail> findPayrollReportDetailById(String id, Pageable pageable);
    Page<PayrollReportDetail> findPayrollReportDetailBySummaryIdAndCompanyId(String summaryId, String companyId, Pageable pageable);
    Page<PayrollReportDetail> findPayrollReportDetailBySummaryIdAndCompanyIdAndFullNameContainingIgnoreCase(String summaryId, String companyId, String fullName, Pageable pageable);
}


package com.xykine.computation.repo;

import com.xykine.computation.entity.DashboardGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.xykine.payroll.model.PaymentFrequencyEnum;
import java.util.Optional;

public interface DashboardGraphRepo extends MongoRepository<DashboardGraph,String> {
    Page<DashboardGraph> findDashboardGraphByPaymentFrequencyAndCompanyIdOrderByDateAddedDesc(PaymentFrequencyEnum paymentFrequencyEnum, String companyId, Pageable pageable);
    Page<DashboardGraph> findDashboardGraphByCompanyIdOrderByDateAddedDesc(String companyId, Pageable pageable);
    Optional<DashboardGraph> findByCompanyIdAndStartDateAndEndDate(String companyId, String startDate, String endDate);
    void deleteByCompanyIdAndStartDateAndEndDate(String companyId, String startDate, String endDate);
}

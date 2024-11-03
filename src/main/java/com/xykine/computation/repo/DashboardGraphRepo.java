package com.xykine.computation.repo;

import com.xykine.computation.entity.DashboardGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.xykine.payroll.model.PaymentFrequencyEnum;

public interface DashboardGraphRepo extends MongoRepository<DashboardGraph,String> {
    Page<DashboardGraph> findDashboardGraphByPaymentFrequencyAndCompanyIdOrderByDateAddedDesc(PaymentFrequencyEnum paymentFrequencyEnum, String companyId, Pageable pageable);
}

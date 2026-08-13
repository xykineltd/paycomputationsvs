package com.xykine.computation.reconciliation.run;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollReconciliationTempRepository extends MongoRepository<PayrollReconciliationTemp, String> {
    List<PayrollReconciliationTemp> findByCompanyIdAndReportId(String companyId, String reportId);

    Optional<PayrollReconciliationTemp> findById(String id);
}

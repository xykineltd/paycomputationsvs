package com.xykine.computation.service;


import com.xykine.computation.entity.PayrollStatus;
import com.xykine.computation.repo.PayrollReportDetailStatusUpdater;
import com.xykine.computation.request.UpdatePayrollStatusRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PayrollReportDetailStatusService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayrollReportDetailStatusService.class);

    private final PayrollReportDetailStatusUpdater updater;

    /**
     * If companyId is null, update ALL rows.
     * If companyId is provided, update only that company.
     */
    public long updatePayrollStatus(PayrollStatus status, String companyId) {
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("Company ID must be provided");
        }
        return updateByCompany(companyId, status);
    }

    private long updateAll(PayrollStatus status) {
        long updated = updater.updateAllStatuses(status);
        LOGGER.info("Updated ALL rows to status: {}, count: {}" , status, updated);
        return updated;
    }

    private long updateByCompany(String companyId, PayrollStatus status) {
        long updated = updater.updateStatusesByCompany(companyId, status);
        LOGGER.info("Updated rows for company {} to status: {}, count: {}", companyId, status, updated);
        return updated;
    }

    public void updateByCompanyAndReport(UpdatePayrollStatusRequest request) {
        long updated = updater.updateStatusesByCompanyAndReport(
                request.getCompanyId(),
                String.valueOf(request.getReportId()),
                request.getStatus());
        LOGGER.info("Updated rows for company {} and reportId {} to status {}, count= {}",
                request.getCompanyId(), request.getReportId(), request.getStatus(), updated);
    }
}

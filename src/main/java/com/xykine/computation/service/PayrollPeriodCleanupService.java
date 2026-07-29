package com.xykine.computation.service;

import com.xykine.computation.entity.PayrollReportSummary;
import com.xykine.computation.entity.PayrollStatus;
import com.xykine.computation.repo.PayrollReportDetailRepo;
import com.xykine.computation.repo.PayrollReportSummaryRepo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Deletes prior non-finalized reports for a company/period so payroll re-runs are idempotent.
 */
@Service
@RequiredArgsConstructor
public class PayrollPeriodCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayrollPeriodCleanupService.class);

    private final PayrollReportSummaryRepo payrollReportSummaryRepo;
    private final PayrollReportDetailRepo payrollReportDetailRepo;

    public void deletePriorReportsForPeriod(String companyId, String startDate, boolean offCycle, String offCycleId) {
        List<PayrollReportSummary> existing = payrollReportSummaryRepo
                .findAllyByStartDateAndCompanyIdAndOffCycle(startDate, companyId, offCycle);
        for (PayrollReportSummary summary : existing) {
            if (summary.getPayrollStatus() == PayrollStatus.DISBURSED
                    || summary.getPayrollStatus() == PayrollStatus.COMPLETED) {
                continue;
            }
            if (offCycle && offCycleId != null && summary.getOffCycleId() != null
                    && !offCycleId.equals(summary.getOffCycleId())) {
                continue;
            }
            payrollReportDetailRepo.deleteAllBySummaryIdAndCompanyId(summary.getId().toString(), companyId);
            payrollReportSummaryRepo.deleteById(summary.getId());
            LOGGER.info("Deleted prior report {} for company {} startDate {}", summary.getId(), companyId, startDate);
        }
    }
}

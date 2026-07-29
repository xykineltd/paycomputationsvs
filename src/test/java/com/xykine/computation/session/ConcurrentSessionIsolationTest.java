package com.xykine.computation.session;

import com.xykine.computation.utils.ComputationUtils;
import org.junit.jupiter.api.Test;
import org.xykine.payroll.model.PaymentInfo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentSessionIsolationTest {

    @Test
    void parallelJobsDoNotShareSessionState() {
        SessionCalculationObject jobA = new SessionCalculationObject();
        SessionCalculationObject jobB = new SessionCalculationObject();
        jobA.setCostCenterSummary(new ConcurrentHashMap<>());
        jobB.setCostCenterSummary(new ConcurrentHashMap<>());

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int idx = i;
            futures.add(CompletableFuture.runAsync(() -> {
                SessionCalculationObject session = (idx % 2 == 0) ? jobA : jobB;
                PayrollSessionHolder.set(session);
                try {
                    PaymentInfo paymentInfo = new PaymentInfo();
                    paymentInfo.setEmployeeID("E" + idx);
                    paymentInfo.setFullName("Emp " + idx);
                    ComputationUtils.updateReportSummary(paymentInfo, session, "Gross Pay", BigDecimal.TEN);
                } finally {
                    PayrollSessionHolder.clear();
                }
            }));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        assertThat(jobA.getSummary().get("Gross Pay")).isEqualByComparingTo("100");
        assertThat(jobB.getSummary().get("Gross Pay")).isEqualByComparingTo("100");
        assertThat(jobA.getSummaryDetails().get("Gross Pay")).hasSize(10);
        assertThat(jobB.getSummaryDetails().get("Gross Pay")).hasSize(10);
    }
}

package com.xykine.computation.utils;

import com.xykine.computation.exceptions.CompanyAccessDeniedException;
import com.xykine.computation.session.PayrollSessionHolder;
import com.xykine.computation.session.SessionCalculationObject;
import org.junit.jupiter.api.Test;
import org.xykine.payroll.model.PaymentInfo;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComputationUtilsTest {

    @Test
    void updateReportSummaryAccumulatesTotalsAndCostCenter() {
        SessionCalculationObject session = new SessionCalculationObject();
        session.setCostCenters(java.util.Map.of("CC1", List.of("E1")));
        session.setCostCenterSummary(new ConcurrentHashMap<>());

        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setEmployeeID("E1");
        paymentInfo.setFullName("Employee One");
        paymentInfo.setDepartmentName("Finance");

        ComputationUtils.updateReportSummary(paymentInfo, session, "Gross Pay", new BigDecimal("100.00"));
        ComputationUtils.updateReportSummary(paymentInfo, session, "Gross Pay", new BigDecimal("50.00"));

        assertThat(session.getSummary().get("Gross Pay")).isEqualByComparingTo("150.00");
        assertThat(session.getCostCenterSummary().get("CC1").get("Gross Pay")).isEqualByComparingTo("150.00");
        assertThat(session.getSummaryDetails().get("Gross Pay")).hasSize(2);
    }

    @Test
    void roundToTwoDecimalPlacesUsesCeiling() {
        assertThat(ComputationUtils.roundToTwoDecimalPlaces(new BigDecimal("1.001")))
                .isEqualByComparingTo("1.01");
    }
}

class CompanyAccessGuardTest {

    @Test
    void skipsWhenEnforcementDisabled() {
        CompanyAccessGuard guard = new CompanyAccessGuard(false);
        guard.requireCompanyAccess("any-company");
    }

    @Test
    void rejectsBlankCompanyWhenEnforced() {
        CompanyAccessGuard guard = new CompanyAccessGuard(true);
        assertThatThrownBy(() -> guard.requireCompanyAccess(" "))
                .isInstanceOf(CompanyAccessDeniedException.class);
    }
}

class PayrollSessionHolderTest {

    @Test
    void bindsAndClearsSession() {
        SessionCalculationObject session = new SessionCalculationObject();
        PayrollSessionHolder.set(session);
        assertThat(PayrollSessionHolder.get()).isSameAs(session);
        PayrollSessionHolder.clear();
        assertThatThrownBy(PayrollSessionHolder::get)
                .isInstanceOf(IllegalStateException.class);
    }
}

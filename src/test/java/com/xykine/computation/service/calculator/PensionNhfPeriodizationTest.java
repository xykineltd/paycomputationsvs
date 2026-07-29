package com.xykine.computation.service.calculator;

import com.xykine.computation.entity.EmployeeMetadata;
import com.xykine.computation.entity.EmployeeType;
import com.xykine.computation.session.PayrollSessionHolder;
import com.xykine.computation.session.SessionCalculationObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xykine.payroll.model.MapKeys;
import org.xykine.payroll.model.PaymentFrequencyEnum;
import org.xykine.payroll.model.PaymentInfo;
import org.xykine.payroll.model.PaymentSettingsResponse;
import org.xykine.payroll.model.enums.PaymentTypeEnum;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PensionNhfPeriodizationTest {

    @BeforeEach
    void bindSession() {
        SessionCalculationObject session = new SessionCalculationObject();
        session.getComputationConstants().put("pensionFundPercent", new BigDecimal("0.08"));
        session.getComputationConstants().put("employerPensionContributionPercent", new BigDecimal("0.10"));
        session.getComputationConstants().put("nationalHousingFundPercent", new BigDecimal("0.025"));
        PayrollSessionHolder.set(session);
    }

    @AfterEach
    void clear() {
        PayrollSessionHolder.clear();
    }

    @Test
    void compute_keepsAnnualNhfForReliefAndPeriodAmountForDeduction() {
        PaymentInfo info = new PaymentInfo();
        info.setEmployeeID("e1");
        info.setStartDate("2026-01-01");
        info.setBasicSalary(BigDecimal.valueOf(1_200_000));

        Set<PaymentSettingsResponse> settings = new HashSet<>();
        PaymentSettingsResponse basic = new PaymentSettingsResponse();
        basic.setType(PaymentTypeEnum.BASIC_SALARY_ANNUAL);
        basic.setName("Basic");
        basic.setValue(BigDecimal.valueOf(1_200_000));
        settings.add(basic);
        info.setPaymentSettings(settings);

        EmployeeMetadata meta = new EmployeeMetadata();
        meta.setEmployeeId("e1");
        meta.setEmployeeType(EmployeeType.FULL_TIME);
        meta.setPensioned(true);
        meta.setNHFSubscribed(true);
        meta.setVoluntaryPensionContribution(BigDecimal.ZERO);

        PensionNhfCalculator.Result result =
                PensionNhfCalculator.compute(info, meta, PaymentFrequencyEnum.MONTHLY);

        // Annual NHF = 2.5% of 1_200_000 = 30_000 (for relief)
        assertThat(result.annualNhf()).isEqualByComparingTo("30000.00");
        // Period NHF = 30_000 / 12 = 2_500 (for deduction map)
        assertThat(result.nhf().get(MapKeys.NATIONAL_HOUSING_FUND)).isEqualByComparingTo("2500.00");
        // Annual employee pension = 8% of basic = 96_000
        assertThat(result.annualEmployeePension()).isEqualByComparingTo("96000.00");
        assertThat(result.pension().get(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION))
                .isEqualByComparingTo("8000.00");
    }
}

package com.xykine.computation.service.calculator;

import com.xykine.computation.entity.EmployeeMetadata;
import com.xykine.computation.entity.EmployeeType;
import com.xykine.computation.entity.Tax;
import com.xykine.computation.session.PayrollSessionHolder;
import com.xykine.computation.session.SessionCalculationObject;
import com.xykine.computation.utils.PayrollMapKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xykine.payroll.model.MapKeys;
import org.xykine.payroll.model.PaymentFrequencyEnum;
import org.xykine.payroll.model.PaymentInfo;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxReliefAndPayeEngineTest {

    @BeforeEach
    void bindSession() {
        SessionCalculationObject session = new SessionCalculationObject();
        session.getComputationConstants().put("craFraction", new BigDecimal("0.01"));
        session.getComputationConstants().put("craCutOff", new BigDecimal("200000"));
        session.getComputationConstants().put("variableCRAFraction", new BigDecimal("0.2"));
        session.setOffCycleTaxable(true);
        PayrollSessionHolder.set(session);
    }

    @AfterEach
    void clearSession() {
        PayrollSessionHolder.clear();
    }

    @Test
    void computePayeeTax_usesNewTaxBracketsAndPayeKey() {
        PaymentInfo info = basePaymentInfo(false);
        Map<String, BigDecimal> relief = new HashMap<>();
        // monthly chargeable 250_000 → annual 3_000_000 → PAYE annual 330_000 → monthly 27_500
        relief.put(PayrollMapKeys.MONTHLY_CHARGEABLE_INCOME, BigDecimal.valueOf(250_000));
        info.setTaxRelief(relief);

        Tax tax = new Tax();
        tax.setVersion("new");
        tax.setCountry("NIGERIA");
        tax.setActive(true);

        TaxReliefAndPayeEngine.computePayeeTax(info, tax, PaymentFrequencyEnum.MONTHLY, true);

        assertThat(info.getPayeeTax().get(PayrollMapKeys.PAYE))
                .isEqualByComparingTo("27500.00");
        assertThat(info.getPayeeTax().get(PayrollMapKeys.ANNUAL_PAYE_TAX))
                .isEqualByComparingTo("330000.00");
    }

    @Test
    void computePayeeTax_failsWhenChargeableIncomeMissing() {
        PaymentInfo info = basePaymentInfo(false);
        Tax tax = new Tax();
        tax.setVersion("new");

        assertThatThrownBy(() ->
                TaxReliefAndPayeEngine.computePayeeTax(info, tax, PaymentFrequencyEnum.MONTHLY, true))
                .hasMessageContaining("MONTHLY CHARGEABLE INCOME");
    }

    @Test
    void applyRegularRelief_buildsChargeableFromAnnualComponents() {
        PaymentInfo info = basePaymentInfo(false);
        Map<String, BigDecimal> gross = new HashMap<>();
        gross.put(MapKeys.GROSS_PAY, BigDecimal.valueOf(500_000));
        info.setGrossPay(gross);

        EmployeeMetadata meta = new EmployeeMetadata();
        meta.setEmployeeId("e1");
        meta.setEmployeeType(EmployeeType.FULL_TIME);
        meta.setPensioned(true);
        meta.setVoluntaryPensionContribution(BigDecimal.valueOf(10_000));
        meta.setRentAllowance(BigDecimal.valueOf(120_000));
        meta.setCustomTaxReliefApplicable(BigDecimal.ZERO);

        TaxReliefAndPayeEngine.applyRegularRelief(
                info,
                meta,
                BigDecimal.valueOf(96_000),  // annual pension
                BigDecimal.valueOf(30_000),  // annual NHF
                BigDecimal.valueOf(5_000));  // monthly tax-free

        BigDecimal monthlyChargeable = info.getTaxRelief().get(PayrollMapKeys.MONTHLY_CHARGEABLE_INCOME);
        // annual gross 6_000_000 - relief (30k+96k+120k voluntary annual 120k + rent 120k) = 6_000_000 - 366_000 = 5_634_000
        // monthly = 469_500 - 5_000 tax-free = 464_500
        assertThat(monthlyChargeable).isEqualByComparingTo("464500.00");
    }

    private static PaymentInfo basePaymentInfo(boolean offCycle) {
        PaymentInfo info = new PaymentInfo();
        info.setEmployeeID("e1");
        info.setCompanyID("c1");
        info.setOffCycle(offCycle);
        info.setStartDate("2026-01-01");
        info.setEndDate("2026-01-31");
        Map<String, BigDecimal> gross = new HashMap<>();
        gross.put(MapKeys.GROSS_PAY, BigDecimal.valueOf(500_000));
        info.setGrossPay(gross);
        return info;
    }
}

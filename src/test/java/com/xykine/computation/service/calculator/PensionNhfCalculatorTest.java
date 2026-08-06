package com.xykine.computation.service.calculator;

import org.junit.jupiter.api.Test;
import org.xykine.payroll.model.PaymentInfo;
import org.xykine.payroll.model.PaymentSettingsResponse;
import org.xykine.payroll.model.enums.PaymentTypeEnum;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PensionNhfCalculatorTest {

    @Test
    void resolvePensionableBase_includesHousingTransportAndPensionableAllowances() {
        PaymentInfo info = new PaymentInfo();
        info.setBasicSalary(BigDecimal.valueOf(1_200_000));

        Set<PaymentSettingsResponse> settings = new HashSet<>();
        settings.add(setting(PaymentTypeEnum.GROSS_EARNING, "Basic", BigDecimal.valueOf(1_200_000), false));
        settings.add(setting(PaymentTypeEnum.GROSS_EARNING, "Housing", BigDecimal.valueOf(300_000), false));
        settings.add(setting(PaymentTypeEnum.GROSS_EARNING, "Transport", BigDecimal.valueOf(100_000), false));
        settings.add(setting(PaymentTypeEnum.GROSS_EARNING, "Call", BigDecimal.valueOf(50_000), false));
        settings.add(setting(PaymentTypeEnum.GROSS_EARNING, "Pensionable Bonus", BigDecimal.valueOf(80_000), true));
        info.setPaymentSettings(settings);

        BigDecimal annualBasic = PensionNhfCalculator.resolveAnnualBasicSalary(info);
        BigDecimal base = PensionNhfCalculator.resolvePensionableBase(info, annualBasic);

        // basic + housing + transport + pensionable bonus (Call not pensionable)
        assertThat(annualBasic).isEqualByComparingTo("1200000");
        assertThat(base).isEqualByComparingTo("1680000");
    }

    @Test
    void resolveAnnualBasic_fallsBackToPaymentInfoBasicSalary() {
        PaymentInfo info = new PaymentInfo();
        info.setBasicSalary(BigDecimal.valueOf(900_000));
        info.setPaymentSettings(new HashSet<>());
        assertThat(PensionNhfCalculator.resolveAnnualBasicSalary(info)).isEqualByComparingTo("900000");
    }

    private static PaymentSettingsResponse setting(
            PaymentTypeEnum type, String name, BigDecimal value, boolean pensionable) {
        PaymentSettingsResponse s = new PaymentSettingsResponse();
        s.setType(type);
        s.setName(name);
        s.setValue(value);
        s.setPensionable(pensionable);
        return s;
    }
}

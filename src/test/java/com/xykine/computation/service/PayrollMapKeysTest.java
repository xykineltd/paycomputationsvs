package com.xykine.computation.service;

import com.xykine.computation.utils.PayrollMapKeys;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lightweight unit checks for payroll map-key conventions used by YTD/variance.
 */
class PayrollMapKeysTest {

    @Test
    void payeKeyIsCanonical() {
        Map<String, BigDecimal> payeeTax = new HashMap<>();
        payeeTax.put(PayrollMapKeys.PAYE, new BigDecimal("1200.00"));
        assertThat(payeeTax.get(PayrollMapKeys.PAYE)).isEqualByComparingTo("1200.00");
        assertThat(payeeTax.get("Monthly Paye")).isNull();
    }
}

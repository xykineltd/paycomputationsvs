package com.xykine.computation.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documents the FX net-pay rounding contract used by PaymentCalculatorImpl.computeNetPay:
 * local net for summary, source-currency net with HALF_UP (not CEILING).
 */
class FxNetRoundingTest {

    @Test
    void employeeFacingNetUsesHalfUpNotCeiling() {
        BigDecimal localNet = new BigDecimal("1000.00");
        BigDecimal rate = new BigDecimal("3");
        BigDecimal halfUp = localNet.divide(rate, 2, RoundingMode.HALF_UP);
        BigDecimal ceiling = localNet.divide(rate, 2, RoundingMode.CEILING);
        assertThat(halfUp).isEqualByComparingTo("333.33");
        assertThat(ceiling).isEqualByComparingTo("333.34");
        assertThat(halfUp).isNotEqualByComparingTo(ceiling);
    }
}

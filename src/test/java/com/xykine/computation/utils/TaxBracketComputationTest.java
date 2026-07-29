package com.xykine.computation.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TaxBracketComputationTest {

    @Test
    void newTaxRule_zeroBelow800k() {
        assertThat(ComputationUtils.getAnnualTaxAmountBynewTaxRule(BigDecimal.valueOf(800_000)))
                .isEqualByComparingTo("0.00");
        assertThat(ComputationUtils.getAnnualTaxAmountBynewTaxRule(BigDecimal.valueOf(500_000)))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void newTaxRule_firstBand15Percent() {
        // Income 1_000_000 → taxable above 800k = 200_000 @ 15% = 30_000
        assertThat(ComputationUtils.getAnnualTaxAmountBynewTaxRule(BigDecimal.valueOf(1_000_000)))
                .isEqualByComparingTo("30000.00");
    }

    @Test
    void newTaxRule_at3MillionBoundary() {
        // 800k@0 + 2.2M@15% = 330_000
        assertThat(ComputationUtils.getAnnualTaxAmountBynewTaxRule(BigDecimal.valueOf(3_000_000)))
                .isEqualByComparingTo("330000.00");
    }

    @Test
    void newTaxRule_into18PercentBand() {
        // 330_000 + (1_000_000 * 0.18) = 330_000 + 180_000 = 510_000 at 4M
        assertThat(ComputationUtils.getAnnualTaxAmountBynewTaxRule(BigDecimal.valueOf(4_000_000)))
                .isEqualByComparingTo("510000.00");
    }

    @Test
    void monthlyNewTaxRule_dividesAnnualBy12() {
        BigDecimal annual = ComputationUtils.getAnnualTaxAmountBynewTaxRule(BigDecimal.valueOf(3_000_000));
        BigDecimal monthly = ComputationUtils.getMonthlyTaxAmountBynewTaxRule(
                BigDecimal.valueOf(3_000_000).divide(BigDecimal.valueOf(12)));
        // getMonthlyTaxAmountBynewTaxRule annualizes input first
        assertThat(monthly.multiply(BigDecimal.valueOf(12))).isEqualByComparingTo(annual);
    }
}

package com.xykine.computation.reconciliation.run;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

class ReconciliationValueSupportTest {

    @Test
    void textAmountsWithDifferentScaleAreEqual() {
        assertTrue(ReconciliationValueSupport.valuesEqual("7000", "7000.00", "text", null));
        assertTrue(ReconciliationValueSupport.valuesEqual("7000", new BigDecimal("7000.00"), "text", null));
        assertTrue(ReconciliationValueSupport.valuesEqual(7000, "7000.00", "text", null));
        assertTrue(ReconciliationValueSupport.valuesEqual("7,000", "7000.00", "text", null));
    }

    @Test
    void differentTextAmountsAreNotEqual() {
        assertFalse(ReconciliationValueSupport.valuesEqual("7000", "7001.00", "text", null));
    }

    @Test
    void employeeCodesStayStringCompared() {
        assertFalse(ReconciliationValueSupport.valuesEqual("MON0311", "311", "text", null));
        assertTrue(ReconciliationValueSupport.valuesEqual("MON0311", "mon0311", "text", null));
    }

    @Test
    void moneyTypeStillTreatsScaleAsEqual() {
        assertTrue(ReconciliationValueSupport.valuesEqual("7000", "7000.00", "money", null));
    }

    @Test
    void rentReliefConvertsAnnualSystemValueToMonthlyWhenGreaterThanZero() {
        Map<String, Object> row = Map.of("RENT RELIEF", new BigDecimal("500000"));
        Object monthly = ReconciliationValueSupport.lookupSystemValue(row, "RENT RELIEF", "RENT RELIEF");
        assertEquals(new BigDecimal("41666.67"), monthly);
        assertTrue(ReconciliationValueSupport.valuesEqual("41666.67", monthly, "money", null));
    }

    @Test
    void rentReliefDoesNotConvertZeroOrMissingSystemValue() {
        Map<String, Object> zeroRow = Map.of("RENT RELIEF", BigDecimal.ZERO);
        assertEquals(BigDecimal.ZERO,
                ReconciliationValueSupport.lookupSystemValue(zeroRow, "RENT RELIEF", "RENT RELIEF"));
        Map<String, Object> emptyRow = Map.of();
        assertTrue(ReconciliationValueSupport.isAbsent(
                ReconciliationValueSupport.lookupSystemValue(emptyRow, "RENT RELIEF", "RENT RELIEF")));
    }
}

package com.xykine.computation.reconciliation.run;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}

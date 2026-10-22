package com.xykine.computation.entity;

/**
 * Product identity for a payroll summary. Separate from {@code offCycle}, which still
 * drives calculator behavior (Custom/Final Pay compute like regular with {@code offCycle=false}).
 */
public enum PayrollRunType {
    REGULAR,
    CUSTOM_PAY,
    FINAL_PAY,
    OFF_CYCLE;

    /** Null / unknown legacy rows are treated as regular. */
    public static boolean isRegular(PayrollRunType type) {
        return type == null || type == REGULAR;
    }
}

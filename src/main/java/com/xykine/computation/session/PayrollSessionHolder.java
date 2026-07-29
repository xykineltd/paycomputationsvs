package com.xykine.computation.session;

/**
 * Holds a job-scoped {@link SessionCalculationObject} for the duration of a payroll run.
 * Must never be a Spring singleton — each payroll job sets a fresh instance.
 */
public final class PayrollSessionHolder {

    private static final ThreadLocal<SessionCalculationObject> HOLDER = new ThreadLocal<>();

    private PayrollSessionHolder() {}

    public static void set(SessionCalculationObject session) {
        HOLDER.set(session);
    }

    public static SessionCalculationObject get() {
        SessionCalculationObject session = HOLDER.get();
        if (session == null) {
            throw new IllegalStateException("No SessionCalculationObject bound to the current payroll job");
        }
        return session;
    }

    public static boolean isBound() {
        return HOLDER.get() != null;
    }

    public static void clear() {
        HOLDER.remove();
    }
}

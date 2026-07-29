package com.xykine.computation.session;

/**
 * Holds the job-scoped {@link PayrollCalculationContext} for payroll workers.
 */
public final class PayrollCalculationContextHolder {

    private static final ThreadLocal<PayrollCalculationContext> HOLDER = new ThreadLocal<>();

    private PayrollCalculationContextHolder() {}

    public static void set(PayrollCalculationContext context) {
        HOLDER.set(context);
    }

    public static PayrollCalculationContext get() {
        PayrollCalculationContext context = HOLDER.get();
        if (context == null) {
            throw new IllegalStateException("No PayrollCalculationContext bound to the current payroll job");
        }
        return context;
    }

    public static boolean isBound() {
        return HOLDER.get() != null;
    }

    public static void clear() {
        HOLDER.remove();
    }
}

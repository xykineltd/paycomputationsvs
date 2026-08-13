package com.xykine.computation.reconciliation.run;

public class PayrollReconciliationNotFoundException extends RuntimeException {
    public PayrollReconciliationNotFoundException(String id) {
        super("Payroll reconciliation run not found: " + id);
    }
}

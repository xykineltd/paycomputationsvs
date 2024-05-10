package com.xykine.computation.exceptions;

public class PayrollUnmodifiableException extends RuntimeException {
    public PayrollUnmodifiableException(String startDate) {
        super("The payroll for this pay period has already been approved and processed and cannot be altered.");
    }
}
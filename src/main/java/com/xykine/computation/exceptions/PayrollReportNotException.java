package com.xykine.computation.exceptions;

public class PayrollReportNotException extends RuntimeException {
    public PayrollReportNotException(String startDate) {
        super("The payroll for the pay period " + startDate + " was not found.");
    }
}
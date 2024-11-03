package com.xykine.computation.exceptions;

public class PayrollValidationException extends RuntimeException {
    public PayrollValidationException(String message) {
        super(message);
    }
}
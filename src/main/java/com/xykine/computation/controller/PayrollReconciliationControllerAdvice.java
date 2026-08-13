package com.xykine.computation.controller;

import com.xykine.computation.reconciliation.run.PayrollReconciliationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice(assignableTypes = PayrollReconciliationRunController.class)
public class PayrollReconciliationControllerAdvice {

    @ExceptionHandler(PayrollReconciliationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> notFound(RuntimeException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> badRequest(RuntimeException ex) {
        return Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Bad request");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    Map<String, String> fileTooLarge(MaxUploadSizeExceededException ex) {
        return Map.of("message", "Uploaded Excel file is too large. Maximum size is 100MB.");
    }
}

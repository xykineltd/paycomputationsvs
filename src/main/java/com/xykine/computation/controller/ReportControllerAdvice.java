package com.xykine.computation.controller;

import com.xykine.computation.exceptions.PayrollInternalServerError;
import com.xykine.computation.exceptions.PayrollReportNotException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class ReportControllerAdvice {

    @ExceptionHandler(PayrollReportNotException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String payrollNotFound(PayrollReportNotException ex) {
        return ex.getMessage();
    }
}

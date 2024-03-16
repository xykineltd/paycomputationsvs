package com.xykine.computation.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class PaymentInfo {
    private Long id;
    private int numberOfDaysOfUnpaidAbsence;
    private int numberOfHours;
    private Map<String, BigDecimal> deduction;
    private Map<String, BigDecimal> earning;
    private Map<String, BigDecimal> others;
    private String startDate;
    private String endDate;
    private String bandCode;
    private BigDecimal basicSalary;
    private Employee employee;
    private BigDecimal hourlyRate;
    private Long companyID;

    private String fullName;

    private BigDecimal totalAmountDue;

    private boolean isCompleted;

    private String createdDate;


    private String lastModifiedDate;

    private String createdBy;

    private String lastModifiedBy;

    private int version;
}

package com.xykine.computation.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

@Data
public class PaymentInfo implements Serializable {
    private Long id;
    private int numberOfDaysOfUnpaidAbsence;
    private int numberOfHours;
    private Map<String, BigDecimal> deduction;
    private Map<String, BigDecimal> grossPay;
    private Map<String, BigDecimal> taxRelief;
    private Map<String, BigDecimal> payeeTax;
    private Map<String, BigDecimal> earning;
    private Map<String, BigDecimal> others;
    private String startDate;
    private String endDate;
    private String bandCode;
    private BigDecimal basicSalary;
    private BigDecimal totalAmountDue;
    private Employee employee;
    private BigDecimal hourlyRate;
    private Long companyID;

    private String fullName;

    private BigDecimal netPay;

    private boolean isCompleted;

    private String createdDate;


    private String lastModifiedDate;

    private String createdBy;

    private String lastModifiedBy;

    private int version;
}

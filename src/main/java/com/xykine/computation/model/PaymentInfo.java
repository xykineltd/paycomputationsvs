package com.xykine.computation.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

@Data
public class PaymentInfo implements Serializable {
    private String id;
    private int numberOfDaysOfUnpaidAbsence;
    private int numberOfHours;
    private String departmentId;
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
    private String employeeID;
    private BigDecimal hourlyRate;
    private String companyID;

    private Set<PaymentSettings> paymentSettings;

    private String fullName;

    private BigDecimal netPay;

    private boolean completed;
    private boolean employeeIsLock;

    private String createdDate;


    private String lastModifiedDate;

    private String createdBy;

    private String lastModifiedBy;

    private int version;
}

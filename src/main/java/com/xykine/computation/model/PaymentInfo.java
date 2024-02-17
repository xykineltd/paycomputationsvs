package com.xykine.computation.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentInfo {
    private String employeeId;
    private String companyId;
    private String departmentId;
    private String fullName;
    private String taxClass;
    private Integer numberOfDaysOfUnpaidAbsence;
    private Integer numberOfHours;
    private BigDecimal basicSalary;
    private BigDecimal hourlyRate;
    private BigDecimal totalDeduction;
    private BigDecimal totalEarning;
    private BigDecimal totalPaymentDue;
    private BigDecimal others;
}

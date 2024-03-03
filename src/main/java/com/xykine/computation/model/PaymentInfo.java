package com.xykine.computation.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class PaymentInfo {
    private String id;
    private String companyId;
    private String departmentId;
    private String fullName;
    private String taxClass;
    private Integer numberOfDaysOfUnpaidAbsence;
    private Integer numberOfHours;
    private BigDecimal basicSalary;
    private BigDecimal hourlyRate;
    private String bandCode;
    private Map<String, BigDecimal> deduction;
    private Map<String, BigDecimal> earning;
    private Map<String, BigDecimal> others;
    private BigDecimal totalAmountDue;
    private Employee employee;
    private String startDate;
    private String endDate;
    private String createdDate;
    private String lastModifiedDate;
    private String createdBy;
    private String lastModifiedBy;
    int version;
}

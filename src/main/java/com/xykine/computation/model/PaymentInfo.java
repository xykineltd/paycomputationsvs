package com.xykine.computation.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

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
    private String band;
    private Map<String, BigDecimal>  deduction;
    private Map<String, BigDecimal> earning;
    private Map<String, BigDecimal> others;
    private LocalDate startDate;
    private LocalDate endDate;
}

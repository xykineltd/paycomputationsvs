package com.xykine.computation.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public class PaymentResponse {
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
    private Map<String, BigDecimal> deductionMap;
    private Map<String, BigDecimal> earningMap;
     private Map<String, BigDecimal> othersMap;
    private LocalDate startDate;
    private LocalDate endDate;
}

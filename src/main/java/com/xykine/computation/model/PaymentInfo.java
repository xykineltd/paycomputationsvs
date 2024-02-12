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
    private BigDecimal basicSalary;
    private BigDecimal pfaDeductionPercentage;
    private String pfaName;
    private String pfaAccount;
    private BigDecimal totalBonusDue;
    private BigDecimal totalDeduction;
    private BigDecimal totalPaymentDue;
}

package com.xykine.computation.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Document
public class YTDReport {
    @Id
    private String id;
    private String employeeId;
    private String companyId;
    private BigDecimal basicSalary;
    private BigDecimal grossPay;
    private BigDecimal netPay;
    private BigDecimal nhf;
    private BigDecimal payeeTax;
    private BigDecimal employeeContributedPension;
    private BigDecimal employerContributedPension;

}

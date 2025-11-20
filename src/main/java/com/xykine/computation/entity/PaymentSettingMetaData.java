package com.xykine.computation.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Document
public class PaymentSettingMetaData {
    @Id
    private String id;
    private String employeeId;
    private String companyId;
    private String paymentType;   // ALLOWANCE,  DEDUCTION
    private String paymentName;
    private BigDecimal paymentAmount;
    private Boolean prorated;
    private Boolean taxable;
    private LocalDate startDate;
    private LocalDate endDate;
}

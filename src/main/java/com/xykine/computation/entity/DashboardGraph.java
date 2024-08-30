package com.xykine.computation.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.xykine.payroll.model.PaymentFrequencyEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Document
public class DashboardGraph {
    @Id
    private String id;
    private String companyId;
    private String startDate;
    private String endDate;
    private PaymentFrequencyEnum paymentFrequency;
    private BigDecimal netPay;
    private LocalDateTime dateAdded;
}

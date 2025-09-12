package com.xykine.computation.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.xykine.payroll.model.PaymentFrequencyEnum;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Document
public class CompanyMetadata {
    @Id
    private String id;
    private String companyId;
    private String companyName;
    private PaymentFrequencyEnum paymentEntryMode;
    private PaymentFrequencyEnum salaryFrequency;
}

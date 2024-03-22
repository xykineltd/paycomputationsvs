package com.xykine.computation.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Document
public class PensionFund {
    @Id
    private String employeeId;
    private String PFACode;
    private Long account;
    private BigDecimal percentage;
    private Instant createdOn;
}

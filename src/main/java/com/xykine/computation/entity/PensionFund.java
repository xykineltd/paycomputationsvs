package com.xykine.computation.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class PensionFund {
    @Id
    private Long employeeId;
    private String PFACode;
    private Long account;
    private BigDecimal percentage;
    private Instant createdOn;
}

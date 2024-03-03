package com.xykine.computation.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class T511K {
    @Id
    private String constant;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal amount;
}

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
public class Deductions {
    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String id;
    private Long employeeId;
    private String description;
    private BigDecimal amount;
    private String createdBy;
    private String approvedBy;
    private Boolean active;
    Instant startDate;
    Instant endDate;
}

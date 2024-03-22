package com.xykine.computation.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;


@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Document
public class PayrollReport {
    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String id;
//    @Column(unique=true)
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean payrollSimulation;
    private boolean payrollApproved;
    private LocalDateTime createdDate;
    private byte[] report;
}

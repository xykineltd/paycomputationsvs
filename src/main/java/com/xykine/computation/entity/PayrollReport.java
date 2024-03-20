package com.xykine.computation.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class PayrollReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
//    @Column(unique=true)
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean payrollSimulation;
    private byte[] report;
    private boolean payrollApproved;
    private Instant createdDate;
}

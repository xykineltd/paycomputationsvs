package com.xykine.computation.entity;

import com.xykine.computation.model.TaxBearer;
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
public class AllowanceAndOtherPayments {
    @Id
    private String id;
    private String bandCode;
    private String description;
    private BigDecimal amount;
    private BigDecimal taxPercent;
    private TaxBearer taxBearer;
    private String createdBy;
    private String approvedBy;
    private Boolean active;
    private LocalDate startDate;
    private LocalDate endDate;
}

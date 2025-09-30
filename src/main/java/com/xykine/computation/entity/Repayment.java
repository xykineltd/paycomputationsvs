package com.xykine.computation.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document("repayments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Repayment {
    @Id
    private String id;

    @Indexed
    private String loanId;
    @Indexed private String companyId;
    @Indexed private String employeeId;

    private BigDecimal amount;
    private Instant paidAt;         // when the repayment happened
    private String reference;       // optional note/receipt no.

    @CreatedDate
    private Instant createdAt;
}

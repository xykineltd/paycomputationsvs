package com.xykine.computation.entity;

import com.xykine.computation.domain.LoanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document("loans")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Loan {

    @Id private String id;
    @Indexed private String companyId;
    @Indexed private String employeeId;
    public static final Instant MIN_DATE = Instant.EPOCH;

    private BigDecimal principalAmount;
    private BigDecimal outstandingAmount;
    private BigDecimal scheduledRepaymentAmount;
    private String description;
    private LoanStatus status;
    private boolean active;

    private String approvedBy;
    private Instant approvedAt;

    @CreatedDate private Instant createdAt;
    @LastModifiedDate private Instant updatedAt;
}

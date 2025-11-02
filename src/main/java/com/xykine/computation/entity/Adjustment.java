package com.xykine.computation.entity;

import com.xykine.computation.domain.AdjustmentStatus;
import com.xykine.computation.domain.AdjustmentType;
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


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("adjustments")
public class Adjustment {
    @Id
    private String id;

    @Indexed
    private String loanId;
    @Indexed private String companyId;
    @Indexed private String employeeId;

    private AdjustmentType type;         // INCREASE/DECREASE
    private BigDecimal amount;           // magnitude of change
    private String reason;

    private AdjustmentStatus status;     // PENDING/APPROVED/REJECTED
    private String approvedBy;
    private Instant approvedAt;

    @CreatedDate
    private Instant createdAt;
}

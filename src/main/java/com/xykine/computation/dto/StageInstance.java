package com.xykine.computation.dto;


import com.xykine.computation.entity.PayrollStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document(collection = "stage_instances")
public class StageInstance {
    @Id
    private String id;

    // Business keys
    @Indexed
    private UUID payrollId;          // the run this instance belongs to
    private String stageId;            // reference to Stage
    private StageEntity entity;        // denormalized from Stage
    private Integer stepNumber;        // denormalized from Stage to simplify queries
    private String name;

    private long numberOfEmployees;
    private long numberOfPays;
    private BigDecimal netPay;

    @Indexed
    private String companyId;// denormalized from Stage

    // Execution
    @Indexed
    private PayrollStatus status;     // PENDING/IN_PROGRESS/APPROVED/REJECTED/SKIPPED
    private String executedById;       // who started/completed it (creator or approver)
    private String approverId;         // who is expected to approve
    private Instant dueDate;
    private List<PAYROLL_ACTIONS> actions = new ArrayList<>();

    // Timestamps
    @CreatedDate
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;

    private String remarks;            // notes on completion, rejection reason, etc.

    @LastModifiedDate
    private Instant updatedAt;

    // optimistic concurrency (prevents double-complete races)
    @Version
    private Long version;
}

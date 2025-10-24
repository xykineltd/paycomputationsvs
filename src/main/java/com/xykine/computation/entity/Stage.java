package com.xykine.computation.entity;

import com.xykine.computation.request.WorkflowDTOs.PAYROLL_ACTIONS;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document("stages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stage {
    @Id
    private String id;

    @Indexed
    private String companyId;

    private StageEntity entity;        // e.g., PAYROLL
    private Integer stepNumber;        // e.g., 1, 2, 3
    private String name;               // e.g., "Payroll Submission"
    private String description;        // human-friendly description
    private String approverId;
    private String approverName; // user/role id expected to approve this step

    private List<PAYROLL_ACTIONS> actions;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
}

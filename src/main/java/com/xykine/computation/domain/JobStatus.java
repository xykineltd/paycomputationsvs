package com.xykine.computation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JobStatus {
    private String jobId;
    private String status; // QUEUED, IN_PROGRESS, COMPLETED, FAILED
    private String message;
    private String reportId;
    private String companyId;
}

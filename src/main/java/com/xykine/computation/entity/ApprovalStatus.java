package com.xykine.computation.entity;

public enum ApprovalStatus {
    SUBMITTED,     // created as “submitted by user” (first step)
    PENDING,       // created and waiting for action
    IN_PROGRESS,   // optional (not used in flows below, but kept)
    APPROVED,
    REJECTED,
    SKIPPED
}

package com.xykine.computation.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class ReportResponse {
    PaymentComputeResponse report;
    boolean payrollApproved;
    String createdDate;
}

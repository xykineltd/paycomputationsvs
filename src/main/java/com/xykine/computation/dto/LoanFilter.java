package com.xykine.computation.dto;


import com.xykine.computation.domain.LoanStatus;
import lombok.Data;

import java.time.Instant;

 @Data
public class LoanFilter {
    private String companyId;     // required
    private String employeeId;    // optional
    private LoanStatus status;    // optional
    private Instant createdFrom;  // optional
    private Instant createdTo;    // optional
}
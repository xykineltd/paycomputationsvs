package com.xykine.computation.request;

import lombok.Data;

import java.time.Instant;
import java.util.Set;

@Data
public class PaymentInfoRequest {
    Long companyId;
    Set<Long> departmentId;
    Set<Long> employeeId;
    Instant start;
    Instant end;
}

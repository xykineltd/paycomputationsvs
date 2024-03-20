package com.xykine.computation.request;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.Set;

@Data
public class PaymentInfoRequest {
    Long companyId;
    Set<Long> departmentId;
    Set<Long> employeeId;
    LocalDate start;
    LocalDate end;
    boolean payrollSimulation;
}

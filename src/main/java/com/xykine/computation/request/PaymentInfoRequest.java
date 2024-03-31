package com.xykine.computation.request;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.Set;

@Data
public class PaymentInfoRequest {
    String companyId;
    Set<String> departmentId;
    Set<String> employeeId;
    LocalDate start;
    LocalDate end;
    boolean payrollSimulation;
}

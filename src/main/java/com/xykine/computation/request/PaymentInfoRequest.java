package com.xykine.computation.request;

import lombok.Data;
import org.xykine.payroll.model.PaymentFrequencyEnum;


import java.time.LocalDate;
import java.util.Set;

@Data
public class PaymentInfoRequest {
    String companyId;
    Set<String> departmentId;
    Set<String> employeeId;
    LocalDate start;
    LocalDate end;
    String offCycleID;
    boolean payrollSimulation;
    boolean offCycle;
    PaymentFrequencyEnum paymentFrequency;
}

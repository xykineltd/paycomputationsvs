package com.xykine.computation.request;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class PaymentComputeRequest {
    private String companyId;
    private String departmentId;
    private List<String> employeeIds;
    private Date start;
    private Date end;
}

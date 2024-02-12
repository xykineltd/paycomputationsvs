package com.xykine.computation.request;

import lombok.Data;

import java.util.Date;

@Data
public class PaymentComputeRequest {
    private String companyId;
    private String departmentId;
    private Date start;
    private Date end;
}

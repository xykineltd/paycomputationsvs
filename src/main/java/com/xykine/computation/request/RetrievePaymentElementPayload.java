package com.xykine.computation.request;

import lombok.Data;

import java.util.List;

@Data
public class RetrievePaymentElementPayload {
    private List<String> selectedHeader;   //  MapKeys.NET_PAY  or "Net Pay"   for Net Pay
    private String companyId;
    private String reportId;
}

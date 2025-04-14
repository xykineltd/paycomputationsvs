package com.xykine.computation.request;

import lombok.Data;


@Data
public class RetrieveSummaryElementRequest {
    private String companyId;
    private String reportId;
}

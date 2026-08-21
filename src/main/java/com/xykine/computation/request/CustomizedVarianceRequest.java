package com.xykine.computation.request;


import lombok.Data;

import java.util.List;

@Data
public class CustomizedVarianceRequest {
    private String reportId;
    private List<String> employeeIds;
}

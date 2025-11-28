package com.xykine.computation.request;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;


@Data
public class YtdRequest {
    private String companyId;
    private List<String> employeeIds = new ArrayList<>();
}

package com.xykine.computation.entity;

import lombok.Data;

@Data
public class ReportPaginationRequest {
    private int page = 0;
    private int size = 10;
}

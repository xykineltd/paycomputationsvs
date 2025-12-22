package com.xykine.computation.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PaginatedReportSummaryResponse {
    private int currentPage;
    private long totalItems;
    private int totalPages;
    private List<ReportSummaryResponse> items;
}


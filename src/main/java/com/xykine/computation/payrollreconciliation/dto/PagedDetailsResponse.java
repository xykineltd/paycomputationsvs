package com.xykine.computation.payrollreconciliation.dto;

import com.xykine.computation.payrollreconciliation.entity.ReconciliationDiff;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedDetailsResponse {
    private List<ReconciliationDiff> details;
    private int currentPage;
    private long totalItems;
    private int totalPages;
}

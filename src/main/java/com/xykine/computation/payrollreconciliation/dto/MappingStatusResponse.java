package com.xykine.computation.payrollreconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MappingStatusResponse {
    private boolean ready;
    private String status;
    @Builder.Default
    private List<String> missing = new ArrayList<>();
}

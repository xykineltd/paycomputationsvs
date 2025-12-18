package com.xykine.computation.response;

import com.xykine.computation.request.SelectedEmployeeField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaginatedSelectedEmployeeField {
    private int currentPage;
    private long totalItems;
    private int totalPages;
    private List<SelectedEmployeeField> selectedEmployeeFields;
}

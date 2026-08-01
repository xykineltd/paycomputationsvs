package com.xykine.computation.payrollreconciliation.entity;

import com.xykine.computation.payrollreconciliation.dto.ColumnMapping;
import com.xykine.computation.payrollreconciliation.dto.EntityAlias;
import com.xykine.computation.payrollreconciliation.dto.Tolerances;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "reconciliation_mapping")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationMapping {
    @Id
    private String id;
    @Indexed(unique = true)
    private String companyId;
    private String templateId;
    private int headerRowIndex;
    private int dataStartRow;
    private String excelMatchKey;
    private String systemMatchKey;
    private Tolerances tolerances;
    @Builder.Default
    private List<EntityAlias> entityAliases = new ArrayList<>();
    @Builder.Default
    private List<ColumnMapping> columnMappings = new ArrayList<>();
    private String status; // READY | INCOMPLETE
    private LocalDateTime updatedAt;
}

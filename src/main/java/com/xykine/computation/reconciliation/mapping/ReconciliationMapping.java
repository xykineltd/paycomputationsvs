package com.xykine.computation.reconciliation.mapping;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document("reconciliationMappings")
public class ReconciliationMapping {
    @Id
    private String id;

    @NotBlank(message = "companyId is required")
    @Indexed(unique = true)
    private String companyId;

    private String templateId;
    private Integer headerRowIndex;
    private Integer dataStartRow;
    private String excelMatchKey;
    private String systemMatchKey;

    private ReconciliationTolerances tolerances;

    @Builder.Default
    private List<ReconciliationEntityAlias> entityAliases = new ArrayList<>();

    @Builder.Default
    private List<ReconciliationColumnMapping> columnMappings = new ArrayList<>();

    /** READY | INCOMPLETE | DRAFT — computed on read/save */
    private String status;

    /** Convenience flag mirroring status == READY; not authoritative alone */
    private Boolean ready;

    /** Human-readable missing requirements when incomplete */
    @Builder.Default
    private List<String> missing = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private Instant deletedAt;

    @CreatedBy
    private String createdBy;
}

package com.xykine.computation.reconciliation.mapping;

import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * Read-only access to company reconciliation mappings (owned/written by admin-svc).
 */
@Service
public class ReconciliationMappingService {

    private final ReconciliationMappingRepository repository;

    public ReconciliationMappingService(ReconciliationMappingRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the active mapping for the company, or an unsaved default shell when none exists.
     */
    public ReconciliationMapping getByCompanyId(String companyId) {
        requireCompanyId(companyId);
        return repository.findByCompanyIdAndDeletedAtIsNull(companyId)
                .map(this::withReadiness)
                .orElseGet(() -> defaultShell(companyId));
    }

    private ReconciliationMapping withReadiness(ReconciliationMapping mapping) {
        ReconciliationMappingReadiness.apply(mapping);
        return mapping;
    }

    private ReconciliationMapping defaultShell(String companyId) {
        ReconciliationMapping shell = ReconciliationMapping.builder()
                .companyId(companyId)
                .headerRowIndex(6)
                .dataStartRow(7)
                .excelMatchKey("EMP ID")
                .systemMatchKey("employeeCode")
                .tolerances(ReconciliationTolerances.builder()
                        .money(0.01)
                        .days(0.0)
                        .factor(0.0001)
                        .build())
                .entityAliases(new ArrayList<>())
                .columnMappings(new ArrayList<>())
                .build();
        ReconciliationMappingReadiness.apply(shell);
        return shell;
    }

    private static void requireCompanyId(String companyId) {
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("companyId is required");
        }
    }
}

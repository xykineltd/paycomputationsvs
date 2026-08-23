package com.xykine.computation.service;

import com.xykine.computation.entity.EmployeeMetadata;
import com.xykine.computation.repo.EmployeeMetadataRepo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads employee metadata from Redis first, Mongo second.
 * Save / update / preload always overwrite the Redis entry so payroll
 * sees the latest custom tax and rent values (same idea as company metadata).
 */
@Service
@RequiredArgsConstructor
public class EmployeeMetadataService {

    public static final String CACHE_NAME = "employeeMetadata";

    private static final Logger log = LoggerFactory.getLogger(EmployeeMetadataService.class);

    private final EmployeeMetadataRepo employeeMetadataRepo;
    private final CacheManager cacheManager;

    public Optional<EmployeeMetadata> getByEmployeeId(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return Optional.empty();
        }
        EmployeeMetadata cached = getFromCache(employeeId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<EmployeeMetadata> found = employeeMetadataRepo.findByEmployeeId(employeeId);
        found.ifPresent(this::putInCache);
        return found;
    }

    /**
     * Reloads Mongo into Redis for the company so a payroll run does not
     * keep a stale cached miss or old relief amount.
     */
    public void preloadAllIntoCache(String companyId) {
        List<EmployeeMetadata> allEmployees = employeeMetadataRepo.findByCompanyId(companyId);
        for (EmployeeMetadata employee : allEmployees) {
            putInCache(employee);
        }
        log.info("Preloaded {} employee metadata row(s) into Redis for companyId={}",
                allEmployees.size(), companyId);
    }

    public List<EmployeeMetadata> findByCompanyId(String companyId) {
        return employeeMetadataRepo.findByCompanyId(companyId);
    }

    public List<EmployeeMetadata> findAll() {
        return employeeMetadataRepo.findAll();
    }

    public EmployeeMetadata save(EmployeeMetadata employee) {
        EmployeeMetadata saved = upsert(employee);
        putInCache(saved);
        return saved;
    }

    public List<EmployeeMetadata> saveAll(List<EmployeeMetadata> employees) {
        if (employees == null || employees.isEmpty()) {
            return List.of();
        }
        List<EmployeeMetadata> saved = new ArrayList<>(employees.size());
        for (EmployeeMetadata employee : employees) {
            saved.add(save(employee));
        }
        return saved;
    }

    public Optional<EmployeeMetadata> updateByEmployeeId(String employeeId, EmployeeMetadata updatedEmployee) {
        return employeeMetadataRepo.findByEmployeeId(employeeId).map(existing -> {
            copyFields(existing, updatedEmployee);
            EmployeeMetadata saved = employeeMetadataRepo.save(existing);
            putInCache(saved);
            return saved;
        });
    }

    public void deleteByEmployeeId(String employeeId) {
        employeeMetadataRepo.findByEmployeeId(employeeId)
                .ifPresent(employeeMetadataRepo::delete);
        evictFromCache(employeeId);
    }

    private EmployeeMetadata upsert(EmployeeMetadata incoming) {
        if (incoming == null) {
            throw new IllegalArgumentException("employee metadata is required");
        }
        if (incoming.getEmployeeId() == null || incoming.getEmployeeId().isBlank()) {
            return employeeMetadataRepo.save(incoming);
        }
        return employeeMetadataRepo.findByEmployeeId(incoming.getEmployeeId())
                .map(existing -> {
                    copyFields(existing, incoming);
                    return employeeMetadataRepo.save(existing);
                })
                .orElseGet(() -> employeeMetadataRepo.save(incoming));
    }

    private void copyFields(EmployeeMetadata existing, EmployeeMetadata incoming) {
        if (incoming.getCompanyId() != null) {
            existing.setCompanyId(incoming.getCompanyId());
        }
        if (incoming.getEmployeeType() != null) {
            existing.setEmployeeType(incoming.getEmployeeType());
        }
        existing.setNHFSubscribed(incoming.isNHFSubscribed());
        existing.setPensioned(incoming.isPensioned());
        existing.setCustomTaxReliefApplicable(incoming.getCustomTaxReliefApplicable());
        existing.setVoluntaryPensionContribution(incoming.getVoluntaryPensionContribution());
        existing.setRentAllowance(incoming.getRentAllowance());
    }

    private EmployeeMetadata getFromCache(String employeeId) {
        Cache cache = cache();
        if (cache == null) {
            return null;
        }
        return cache.get(employeeId, EmployeeMetadata.class);
    }

    private void putInCache(EmployeeMetadata employee) {
        if (employee == null || employee.getEmployeeId() == null || employee.getEmployeeId().isBlank()) {
            return;
        }
        Cache cache = cache();
        if (cache != null) {
            cache.put(employee.getEmployeeId(), employee);
        }
    }

    private void evictFromCache(String employeeId) {
        Cache cache = cache();
        if (cache != null && employeeId != null) {
            cache.evict(employeeId);
        }
    }

    private Cache cache() {
        return cacheManager != null ? cacheManager.getCache(CACHE_NAME) : null;
    }
}

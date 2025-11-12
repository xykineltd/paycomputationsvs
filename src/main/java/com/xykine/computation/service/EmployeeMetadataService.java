package com.xykine.computation.service;

import com.xykine.computation.entity.EmployeeMetadata;
import com.xykine.computation.repo.EmployeeMetadataRepo;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmployeeMetadataService {

    private final EmployeeMetadataRepo employeeMetadataRepo;

    @Cacheable(value = "employeeMetadata", key = "#employeeId")
    public Optional<EmployeeMetadata> getByEmployeeId(String employeeId) {
        return employeeMetadataRepo.findByEmployeeId(employeeId);
    }

    public void preloadAllIntoCache(String companyId) {
        List<EmployeeMetadata> allEmployees = employeeMetadataRepo.findByCompanyId(companyId);
        for (EmployeeMetadata employee : allEmployees) {
            getByEmployeeId(employee.getEmployeeId());
        }
    }

    public List<EmployeeMetadata> findByCompanyId(String companyId) {
        return employeeMetadataRepo.findByCompanyId(companyId);
    }

    public List<EmployeeMetadata> findAll() {
        return employeeMetadataRepo.findAll();
    }

    //Save and update cache at the same time
    @CachePut(value = "employeeMetadata", key = "#employee.employeeId")
    public EmployeeMetadata save(EmployeeMetadata employee) {
        return employeeMetadataRepo.save(employee);
    }

    public List<EmployeeMetadata> saveAll(List<EmployeeMetadata> employees) {
        return employeeMetadataRepo.saveAll(employees);
    }


    // Update and refresh the cache entry
    @CachePut(value = "employeeMetadata", key = "#employeeId")
    public Optional<EmployeeMetadata> updateByEmployeeId(String employeeId, EmployeeMetadata updatedEmployee) {
        return employeeMetadataRepo.findByEmployeeId(employeeId).map(existing -> {
            existing.setCompanyId(updatedEmployee.getCompanyId());
            existing.setEmployeeType(updatedEmployee.getEmployeeType());
            existing.setNHFSubscribed(updatedEmployee.isNHFSubscribed());
            existing.setCustomTaxReliefApplicable(updatedEmployee.getCustomTaxReliefApplicable());
            existing.setVoluntaryPensionContribution(updatedEmployee.getVoluntaryPensionContribution());
            return employeeMetadataRepo.save(existing);
        });
    }

    // Evict cache when deleting record
    @CacheEvict(value = "employeeMetadata", key = "#employeeId")
    public void deleteByEmployeeId(String employeeId) {
        employeeMetadataRepo.findByEmployeeId(employeeId)
                .ifPresent(employeeMetadataRepo::delete);
    }
}

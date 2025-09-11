package com.xykine.computation.service;

import com.xykine.computation.entity.EmployeeMetadata;
import com.xykine.computation.repo.EmployeeMetadataRepo;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeMetadataService {

    private final EmployeeMetadataRepo employeeMetadataRepo;

    @Cacheable(value = "employeeMetadata", key = "#employeeId")
    public EmployeeMetadata getByEmployeeId(String employeeId) {
        return employeeMetadataRepo.findByEmployeeId(employeeId)
                .orElse(null);
    }


    public void preloadAllIntoCache(String companyId) {
        List<EmployeeMetadata> allEmployees = employeeMetadataRepo.findAllByCompanyId(companyId);
        for (EmployeeMetadata employee : allEmployees) {
            getByEmployeeId(employee.getEmployeeId());
        }
    }
}

package com.xykine.computation.controller;

import com.xykine.computation.entity.CompanyMetadata;
import com.xykine.computation.entity.EmployeeMetadata;
import com.xykine.computation.service.CompanyMetadataService;
import com.xykine.computation.service.EmployeeMetadataService;
import com.xykine.computation.utils.CompanyAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/compute/metadata")
@RequiredArgsConstructor
public class Metadata {

    private final CompanyMetadataService companyMetadataService;
    private final EmployeeMetadataService employeeMetadataService;
    private final CompanyAccessGuard companyAccessGuard;

    @PostMapping("/company")
    public ResponseEntity<CompanyMetadata> createCompany(@RequestBody CompanyMetadata company) {
        companyAccessGuard.requireCompanyAccess(company.getCompanyId());
        return ResponseEntity.ok(companyMetadataService.save(company));
    }

    @GetMapping("/company/{companyId}")
    public ResponseEntity<?> getCompanyByCompanyId(@PathVariable String companyId) {
        companyAccessGuard.requireCompanyAccess(companyId);
        return companyMetadataService.getByCompanyId(companyId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(Collections.emptyMap()));
    }

    @PutMapping("/company/{companyId}")
    public ResponseEntity<CompanyMetadata> updateCompany(
            @PathVariable String companyId,
            @RequestBody CompanyMetadata updatedCompany
    ) {
        companyAccessGuard.requireCompanyAccess(companyId);
        return companyMetadataService.updateByCompanyId(companyId, updatedCompany)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/company/{companyId}")
    public ResponseEntity<Void> deleteCompany(@PathVariable String companyId) {
        companyAccessGuard.requireCompanyAccess(companyId);
        companyMetadataService.deleteByCompanyId(companyId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/employee")
    public ResponseEntity<EmployeeMetadata> createEmployee(@RequestBody EmployeeMetadata employee) {
        companyAccessGuard.requireCompanyAccess(employee.getCompanyId());
        return ResponseEntity.ok(employeeMetadataService.save(employee));
    }

    @PostMapping("/employee/bulk")
    public ResponseEntity<List<EmployeeMetadata>> createEmployee(@RequestBody List<EmployeeMetadata> employees) {
        employees.forEach(e -> companyAccessGuard.requireCompanyAccess(e.getCompanyId()));
        return ResponseEntity.ok(employeeMetadataService.saveAll(employees));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<EmployeeMetadata> getEmployeeByEmployeeId(
            @PathVariable String employeeId,
            @RequestParam String companyId) {
        companyAccessGuard.requireCompanyAccess(companyId);
        return employeeMetadataService.getByEmployeeId(employeeId)
                .filter(e -> companyId.equals(e.getCompanyId()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/employees/company/{companyId}")
    public ResponseEntity<List<EmployeeMetadata>> getEmployeesByCompanyId(@PathVariable String companyId) {
        companyAccessGuard.requireCompanyAccess(companyId);
        return ResponseEntity.ok(employeeMetadataService.findByCompanyId(companyId));
    }

    @PutMapping("/employee/{employeeId}")
    public ResponseEntity<EmployeeMetadata> updateEmployee(
            @PathVariable String employeeId,
            @RequestParam String companyId,
            @RequestBody EmployeeMetadata updatedEmployee
    ) {
        companyAccessGuard.requireCompanyAccess(companyId);
        return employeeMetadataService.getByEmployeeId(employeeId)
                .filter(e -> companyId.equals(e.getCompanyId()))
                .flatMap(e -> employeeMetadataService.updateByEmployeeId(employeeId, updatedEmployee))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/employee/{employeeId}")
    public ResponseEntity<Void> deleteEmployee(
            @PathVariable String employeeId,
            @RequestParam String companyId) {
        companyAccessGuard.requireCompanyAccess(companyId);
        return employeeMetadataService.getByEmployeeId(employeeId)
                .filter(e -> companyId.equals(e.getCompanyId()))
                .map(e -> {
                    employeeMetadataService.deleteByEmployeeId(employeeId);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

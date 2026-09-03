package com.xykine.computation.controller;

import com.xykine.computation.entity.CompanyMetadata;
import com.xykine.computation.entity.EmployeeMetadata;
import com.xykine.computation.service.CompanyMetadataService;
import com.xykine.computation.service.EmployeeMetadataService;
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

    // ==============================
    // CompanyMetadata CRUD
    // ==============================

    @PostMapping("/company")
    public ResponseEntity<CompanyMetadata> createCompany(@RequestBody CompanyMetadata company) {
        return ResponseEntity.ok(companyMetadataService.save(company));
    }

//    @GetMapping("/company/{companyId}")
//    public ResponseEntity<?> getCompanyByCompanyId(@PathVariable String companyId) {
//        return companyMetadataService.getByCompanyId(companyId)
//                .map(ResponseEntity::ok)
//                .orElse(ResponseEntity.ok(Collections.emptyMap());
//    }

    @GetMapping("/company/{companyId}")
    public ResponseEntity<?> getCompanyByCompanyId(@PathVariable String companyId) {
        return companyMetadataService.getByCompanyId(companyId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(Collections.emptyMap()));
    }


    @GetMapping("/companies")
    public ResponseEntity<List<CompanyMetadata>> getAllCompanies() {
        return ResponseEntity.ok(companyMetadataService.findAll());
    }

    @PutMapping("/company/{companyId}")
    public ResponseEntity<CompanyMetadata> updateCompany(
            @PathVariable String companyId,
            @RequestBody CompanyMetadata updatedCompany
    ) {
        return companyMetadataService.updateByCompanyId(companyId, updatedCompany)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/company/{companyId}")
    public ResponseEntity<Void> deleteCompany(@PathVariable String companyId) {
        companyMetadataService.deleteByCompanyId(companyId);
        return ResponseEntity.noContent().build();
    }

    // ==============================
    // EmployeeMetadata CRUD
    // ==============================

    @PostMapping("/employee")
    public ResponseEntity<EmployeeMetadata> createEmployee(@RequestBody EmployeeMetadata employee) {
        return ResponseEntity.ok(employeeMetadataService.save(employee));
    }

    @PostMapping("/employee/bulk")
    public ResponseEntity<List<EmployeeMetadata>> createEmployee(@RequestBody List<EmployeeMetadata> employee) {
        return ResponseEntity.ok(employeeMetadataService.saveAll(employee));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<EmployeeMetadata> getEmployeeByEmployeeId(@PathVariable String employeeId) {
        //TODO let include companyID here to be completely sure we are pulling employee for a particular company
        return employeeMetadataService.getByEmployeeId(employeeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }


    @GetMapping("/employees/company/{companyId}")
    public ResponseEntity<List<EmployeeMetadata>> getEmployeesByCompanyId(@PathVariable String companyId) {
        return ResponseEntity.ok(employeeMetadataService.findByCompanyId(companyId));
    }

    @DeleteMapping("/employees/company/{companyId}")
    public ResponseEntity<Void> deleteEmployeesByCompanyId(@PathVariable String companyId) {
        employeeMetadataService.deleteByCompanyId(companyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/employees")
    public ResponseEntity<List<EmployeeMetadata>> getAllEmployees() {
        return ResponseEntity.ok(employeeMetadataService.findAll());
    }

    @PutMapping("/employee/{employeeId}")
    public ResponseEntity<EmployeeMetadata> updateEmployee(
            @PathVariable String employeeId,
            @RequestBody EmployeeMetadata updatedEmployee
    ) {

//        System.out.println("updatedEmployee--->" + updatedEmployee);
//        System.out.println("employeeId--->" + employeeId);
        //TODO let include companyID here to be completely sure we are pulling employee for a particular company
        return employeeMetadataService.updateByEmployeeId(employeeId, updatedEmployee)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/employee/{employeeId}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable String employeeId) {
        employeeMetadataService.deleteByEmployeeId(employeeId);
        return ResponseEntity.noContent().build();
    }
}

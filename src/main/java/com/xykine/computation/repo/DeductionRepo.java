package com.xykine.computation.repo;

import com.xykine.computation.entity.Deductions;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeductionRepo extends JpaRepository<Deductions, String> {
    List<Deductions> findDeductionByEmployeeId (String employeeId);
}

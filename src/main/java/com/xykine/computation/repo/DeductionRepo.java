package com.xykine.computation.repo;

import com.xykine.computation.entity.Deductions;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeductionRepo extends JpaRepository<Deductions, Long> {
    List<Deductions> findDeductionByEmployeeId (Long employeeId);
}

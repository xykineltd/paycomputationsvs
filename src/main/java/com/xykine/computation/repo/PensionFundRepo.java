package com.xykine.computation.repo;

import com.xykine.computation.entity.PensionFund;
import com.xykine.computation.entity.Tax;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PensionFundRepo extends JpaRepository<PensionFund, String> {
    PensionFund findPensionFundByEmployeeId(String employeeId);
}

package com.xykine.computation.repo;

import com.xykine.computation.entity.AllowanceAndOtherPayments;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AllowanceAndOtherPaymentsRepo extends JpaRepository<AllowanceAndOtherPayments, String> {
    Optional<AllowanceAndOtherPayments> findById(String id);
    List<AllowanceAndOtherPayments> findByBandCode(String bandCode);

}

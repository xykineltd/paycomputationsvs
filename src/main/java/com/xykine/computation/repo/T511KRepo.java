package com.xykine.computation.repo;


import com.xykine.computation.entity.T511K;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;

public interface T511KRepo extends JpaRepository<T511K, String> {
    T511K findRecordByConstant(String aConstant);
}

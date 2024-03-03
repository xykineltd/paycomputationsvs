package com.xykine.computation.repo;
import com.xykine.computation.entity.Tax;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxRepo extends JpaRepository<Tax, String> {
    Tax findTaxByBand(String band);
}

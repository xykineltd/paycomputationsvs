package com.xykine.computation.repo;
import com.xykine.computation.entity.Tax;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxRepo extends JpaRepository<Tax, String> {
    @Cacheable(value = "taxClass")
    Tax findTaxByTaxClass(String taxClass);
}

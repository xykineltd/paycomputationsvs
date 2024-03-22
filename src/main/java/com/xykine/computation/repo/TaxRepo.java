package com.xykine.computation.repo;
import com.xykine.computation.entity.Tax;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaxRepo extends MongoRepository<Tax,String> {
    @Cacheable(value = "taxClass")
    Tax findTaxByTaxClass(String taxClass);
}

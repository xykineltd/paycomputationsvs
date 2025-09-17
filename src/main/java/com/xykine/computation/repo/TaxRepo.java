package com.xykine.computation.repo;
import com.xykine.computation.entity.Tax;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface TaxRepo extends MongoRepository<Tax,String> {
    @Cacheable(value = "taxRule",  key = "#country")
    @Query(value = "{ 'country': ?0, 'active': true }", fields = "{ 'taxRule': 1, '_id': 0 }")
    String findTaxRuleByCountry(String country);
}

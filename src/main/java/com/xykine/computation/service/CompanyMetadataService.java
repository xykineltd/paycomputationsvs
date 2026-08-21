package com.xykine.computation.service;

import com.xykine.computation.entity.CompanyMetadata;
import com.xykine.computation.repo.CompanyMetaDataRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CompanyMetadataService {

    private final CompanyMetaDataRepo companyMetadataRepo;

    @Cacheable(value = "companyMetadata", key = "#companyId")
    public Optional<CompanyMetadata> getByCompanyId(String companyId) {
        return companyMetadataRepo.findByCompanyId(companyId);
    }

    @Cacheable(value = "companyMetadata", key = "#companyId")
    public CompanyMetadata geCompanyMetadataById(String companyId) {
        return companyMetadataRepo.findByCompanyId(companyId).orElseThrow(() -> new RuntimeException("companyMetadata"));
    }

    public List<CompanyMetadata> findAll() {
        return companyMetadataRepo.findAll();
    }

    @CachePut(value = "companyMetadata", key = "#company.companyId")
    public CompanyMetadata save(CompanyMetadata company) {
        return companyMetadataRepo.save(company);
    }

    @CachePut(value = "companyMetadata", key = "#companyId")
    public Optional<CompanyMetadata> updateByCompanyId(String companyId, CompanyMetadata updatedCompany) {
        return companyMetadataRepo.findByCompanyId(companyId).map(existing -> {
            existing.setCompanyName(updatedCompany.getCompanyName());
            existing.setPaymentEntryMode(updatedCompany.getPaymentEntryMode());
            existing.setSalaryFrequency(updatedCompany.getSalaryFrequency());
            existing.setPaymentDistribution(updatedCompany.getPaymentDistribution());
            return companyMetadataRepo.save(existing);
        });
    }

    @CacheEvict(value = "companyMetadata", key = "#companyId")
    public void deleteByCompanyId(String companyId) {
        companyMetadataRepo.findByCompanyId(companyId)
                .ifPresent(companyMetadataRepo::delete);
    }
}

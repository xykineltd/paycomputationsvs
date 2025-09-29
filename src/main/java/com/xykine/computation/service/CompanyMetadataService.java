package com.xykine.computation.service;

import com.xykine.computation.entity.CompanyMetadata;
import com.xykine.computation.repo.CompanyMetaDataRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CompanyMetadataService {

    private final CompanyMetaDataRepo companyMetadataRepo;

    //TODO the Cacheable is returning even something not in the database bcos we already deleted them
//    @Cacheable(value = "companyMetadata", key = "#companyId")
    public Optional<CompanyMetadata> getByCompanyId(String companyId) {
        return companyMetadataRepo.findByCompanyId(companyId);

    }

    public List<CompanyMetadata> findAll() {
        return companyMetadataRepo.findAll();
    }

    public CompanyMetadata save(CompanyMetadata company) {
        return companyMetadataRepo.save(company);
    }

    public Optional<CompanyMetadata> updateByCompanyId(String companyId, CompanyMetadata updatedCompany) {
        return companyMetadataRepo.findByCompanyId(companyId).map(existing -> {
            existing.setCompanyName(updatedCompany.getCompanyName());
            existing.setPaymentEntryMode(updatedCompany.getPaymentEntryMode());
            existing.setSalaryFrequency(updatedCompany.getSalaryFrequency());
            existing.setPaymentDistribution(updatedCompany.getPaymentDistribution());
            return companyMetadataRepo.save(existing);
        });
    }

    public void deleteByCompanyId(String companyId) {
        companyMetadataRepo.findByCompanyId(companyId)
                .ifPresent(companyMetadataRepo::delete);
    }
}

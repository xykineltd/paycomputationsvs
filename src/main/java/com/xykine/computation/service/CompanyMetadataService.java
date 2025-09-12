package com.xykine.computation.service;

import com.xykine.computation.entity.CompanyMetadata;
import com.xykine.computation.repo.CompanyMetaDataRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CompanyMetadataService {

    private final CompanyMetaDataRepo companyMetaDataRepo;

    @Cacheable(value = "companyMetadata", key = "#companyId")
    public CompanyMetadata getByCompanyId(String companyId) {
        return companyMetaDataRepo.findAByCompanyId(companyId)
                .orElse(null);
    }
}

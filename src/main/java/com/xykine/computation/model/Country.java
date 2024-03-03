package com.xykine.computation.model;

import org.springframework.data.annotation.*;

import java.time.Instant;

public record Country(
        @Id
        Long country_id,
        String countryName,
        String countryCode,
        String countryFlag,

        @CreatedDate
        String createdDate,

        @LastModifiedDate
        String lastModifiedDate,

        @CreatedBy
        String createdBy,

        @LastModifiedBy
        String lastModifiedBy,

        @Version
        int version
) {
    public static Country of(String countryName, String countryCode, String countryFlag) {
        return new Country(null, countryName, countryCode, countryFlag, null, null,
                null, null, 0);
    }
}

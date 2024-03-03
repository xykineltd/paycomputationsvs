package com.xykine.computation.model;

import org.springframework.data.annotation.*;

import java.time.Instant;

public record LGA(
        @Id
        Long lgaId,
        String lgaName,
        String lgaCode,

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
    public static LGA of(String lgaName, String lgaCode) {
        return new LGA(null, lgaName, lgaCode, null, null,
                null, null, 0);
    }
}

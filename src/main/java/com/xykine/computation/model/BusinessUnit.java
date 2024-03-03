package com.xykine.computation.model;

import org.springframework.data.annotation.*;

import java.time.Instant;

public record BusinessUnit(
        @Id
        Long businessUnitID,
        String businessUnitCode,
        String businessUnitName,
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
    public static BusinessUnit of(
            String businessUnitCode,
            String businessUnitName
    ) {
        return new BusinessUnit(
                null,
                businessUnitCode,
                businessUnitName,
                null, null, null, null, 0
        );
    }
}
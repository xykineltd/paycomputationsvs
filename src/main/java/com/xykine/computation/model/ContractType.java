package com.xykine.computation.model;

import org.springframework.data.annotation.*;

import java.time.Instant;

public record ContractType(
        @Id
        Long contractTypeID,
        String contractTypeCode,
        String contractTypeName,
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
    public static ContractType of(
            String contractTypeCode,
            String contractTypeName
    ) {
        return new ContractType(
                null,
                contractTypeCode,
                contractTypeName,
                null, null, null, null, 0
        );
    }
}

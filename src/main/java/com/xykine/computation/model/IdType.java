package com.xykine.computation.model;

import org.springframework.data.annotation.*;

import java.time.Instant;

public record IdType(
        @Id
        Long idTypeID,
        String idTypeCode,
        String idTypeName,
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
    public static IdType of(
            String idTypeCode,
            String idTypeName
    ) {
        return new IdType(
                null,
                idTypeCode,
                idTypeName,
                null, null, null, null, 0
        );
    }
}

package com.xykine.computation.model;

import org.springframework.data.annotation.*;

import java.time.Instant;

public record Band(@Id
                   Long bandID,
                   String bandCode,
                   String bandName,
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
    public static Band of(
            String bandCode,
            String bandName
    ) {
        return new Band(
                null,
                bandCode,
                bandName,
                null, null, null, null, 0
        );
    }
}

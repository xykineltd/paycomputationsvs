package com.xykine.computation.model;

import org.springframework.data.annotation.*;

import java.time.Instant;

public record Position(
        @Id
        Long positionID,
        String positionCode,
        String positionName,
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
    public static Position of(
            String positionCode,
            String positionName
    ) {
        return new Position(
                null,
                positionCode,
                positionName,
                null, null, null, null, 0
        );
    }
}

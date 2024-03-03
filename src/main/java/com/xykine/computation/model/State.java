package com.xykine.computation.model;

import org.springframework.data.annotation.*;

import java.time.Instant;

public record State(
        @Id
        Long stateId,
        String stateName,
        String stateCode,

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
    public static State of(String stateName, String stateCode) {
        return new State(null, stateName, stateCode, null, null,
                null, null, 0);
    }
}

package com.xykine.computation.model;

import org.springframework.data.annotation.*;

import java.time.Instant;

public record City(

        @Id
        Long cityId,
        String cityName,
        String cityCode,

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
    public static City of(String cityName, String cityCode) {
        return new City(null, cityName, cityCode, null, null,
                null, null, 0);
    }
}

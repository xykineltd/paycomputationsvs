package com.xykine.computation.model;

import org.springframework.data.annotation.*;

public record Address(
        @Id
        Long addressId,
        String street,
        City city,
        LGA lga,
        State state,
        String postCode,
        Country country,
        String type,
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
    public static Address of(String street, City city, LGA lga, State state, String postCode, Country country, String type) {
        return new Address(null, street, city, lga, state, postCode, country, type, null, null, null, null,  0 );
    }
}

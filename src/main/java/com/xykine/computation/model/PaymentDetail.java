package com.xykine.computation.model;

import org.springframework.data.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;


public record PaymentDetail(
        @Id
        Long id,
        String name,
        BigDecimal value,

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
    public static PaymentDetail of(
            String name,
            BigDecimal value
    ) {
        return new PaymentDetail(
                null,
                name,
                value,
                null,
                null,
                null,
                null,
                0
        );
    }
}

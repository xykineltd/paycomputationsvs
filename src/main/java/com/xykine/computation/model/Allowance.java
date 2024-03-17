package com.xykine.computation.model;

import org.springframework.data.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;


public record Allowance(
        @Id
        String id,
        String allowanceCode,
        String name,
        BigDecimal value,
        Employee employee,

        boolean isActive,

        @CreatedDate
        Instant createdDate,

        @LastModifiedDate
        Instant lastModifiedDate,

        @CreatedBy
        String createdBy,

        @LastModifiedBy
        String lastModifiedBy,

        @Version
        int version
) {
    public static Allowance of(
            String allowanceCode,
            String name,
            BigDecimal value,
            boolean active
    ) {
        return new Allowance(
                null,
                allowanceCode,
                name,
                value,
                null,
                true,
                null,
                null,
                null,
                null,
                0
        );
    }
}
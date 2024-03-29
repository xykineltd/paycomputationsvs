package com.xykine.computation.model;

import org.springframework.data.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;


public record Allowance(
        @Id
        Long id,
        String allowanceCode,
        String name,
        BigDecimal value,

        boolean isActive,

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
    public static Allowance of(
            String allowanceCode,
            String name,
            BigDecimal value,
            String createdDate,
            String lastModifiedDate
    ) {
        return new Allowance(
                null,
                allowanceCode,
                name,
                value,
                true,
                createdDate,
                lastModifiedDate,
                "test User", // createdBy set to a default value or determined by context
                "test User",
                0
        );
    }

}
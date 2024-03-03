package com.xykine.computation.model;

import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.*;

import java.time.Instant;

public record Department(
        @Id
        Long DepartmentID,
        String departmentName,
        String departmentCode,

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
    public static Department of(
            String departmentName,
            String departmentCode
    ) {
        return new Department(
                null,
                departmentName,
                departmentCode,
                null, null, null, null, 0
        );
    }
}

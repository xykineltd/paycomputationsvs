package com.xykine.computation.model;

import org.springframework.data.annotation.*;

import java.time.Instant;

public record EmployeeSubGroup (
        @Id
        Long employee_sub_group_id,
        String employee_sub_group_code,
        String employee_sub_group_name,

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
    public static EmployeeSubGroup of(
            String employee_sub_group_code,
            String employee_sub_group_name
    ) {
        return new EmployeeSubGroup(
                null,
                employee_sub_group_code,
                employee_sub_group_name,
                null,
                null,
                null,
                null,
                0
        );
    }
}

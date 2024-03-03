package com.xykine.computation.model;

import org.springframework.data.annotation.*;

import java.time.Instant;
import java.util.Set;

public record EmployeeGroup(
        @Id
        Long employee_group_id,
        String employee_group_code,
        String employee_group_name,
        Set<EmployeeSubGroup> employeeSubGroup,
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
    public static EmployeeGroup of(
            String employee_group_code,
            String employee_group_name,
            Set<EmployeeSubGroup> employeeSubGroup
    ) {
        return new EmployeeGroup(
                null,
                employee_group_code,
                employee_group_name,
                employeeSubGroup,
                null,
                null,
                null,
                null,
                0
        );
    }
}

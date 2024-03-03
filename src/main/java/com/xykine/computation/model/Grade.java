package com.xykine.computation.model;

import org.springframework.data.annotation.*;

import java.time.Instant;

public record Grade(@Id
                    Long gradeID,
                    String gradeCode,
                    String gradeName,
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
    public static Grade of(
            String gradeCode,
            String gradeName
    ) {
        return new Grade(
                null,
                gradeCode,
                gradeName,
                null, null, null, null, 0
        );
    }
}

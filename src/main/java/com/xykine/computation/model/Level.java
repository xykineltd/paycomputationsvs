package com.xykine.computation.model;

import org.springframework.data.annotation.*;

import java.time.Instant;

public record Level(@Id
                    Long levelID,
                    String levelCode,
                    String levelName,
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
    public static Level of(
            String levelCode,
            String levelName
    ) {
        return new Level(
                null,
                levelCode,
                levelName,
                null, null, null, null, 0
        );
    }
}

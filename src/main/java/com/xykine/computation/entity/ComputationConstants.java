package com.xykine.computation.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Document
public class ComputationConstants {
    @Id
    private String id;
    private String description;
    private BigDecimal value;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;

}

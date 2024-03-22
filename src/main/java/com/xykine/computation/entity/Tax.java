package com.xykine.computation.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

//@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Document
public class Tax {
    @Id
    private String taxClass;
    private BigDecimal percentage;
    private String description;
}

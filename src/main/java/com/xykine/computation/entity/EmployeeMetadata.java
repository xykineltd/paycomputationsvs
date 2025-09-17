package com.xykine.computation.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Document
public class EmployeeMetadata {
    @Id
    private String id;
    private String employeeId;
    private String companyId;
    private EmployeeType employeeType;
    private boolean isNHFSubscribed;
    private BigDecimal voluntaryPensionContribution;
}

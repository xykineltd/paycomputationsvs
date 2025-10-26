package com.xykine.computation.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@JsonSerialize
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SummaryDetail implements Serializable {
//    private static final long serialVersionUID = -4677589975044662049L; // Manually assigned
    private String employeeId;
    private String employeeName;
    private String departmentName;
    private BigDecimal value;
    private BigDecimal variance;
}

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
    private String employeeName;
    private String departmentName;
    private BigDecimal value;
}

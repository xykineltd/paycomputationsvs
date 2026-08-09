package com.xykine.computation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GLSummary {

    private String glCode;

    private String glDescription;

    private BigDecimal debit;

    private BigDecimal credit;

    private BigDecimal net;
}
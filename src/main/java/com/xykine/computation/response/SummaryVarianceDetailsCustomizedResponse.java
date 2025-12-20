package com.xykine.computation.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SummaryVarianceDetailsCustomizedResponse {
    PayCompteVarianceDetailsCustomized payComputeVarianceDetails;
}


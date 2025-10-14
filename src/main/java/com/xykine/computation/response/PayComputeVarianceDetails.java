package com.xykine.computation.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayComputeVarianceDetails implements Serializable {
    private ConcurrentHashMap<String, Set<SummaryDetail>> summaryDetailsVariance;
}

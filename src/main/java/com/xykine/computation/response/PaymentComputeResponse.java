package com.xykine.computation.response;

import com.xykine.computation.model.PaymentInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class PaymentComputeResponse {
    private boolean success;
    private String message;
    private List<PaymentInfo> report;
    private Map<String, BigDecimal> summary;
    private String start;
    private String end;
}

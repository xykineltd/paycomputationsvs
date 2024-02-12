package com.xykine.computation.response;

import com.xykine.computation.model.PaymentInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class PaymentComputeResponse {
    private boolean success;
    private String message;
    private List<PaymentInfo> report;
}

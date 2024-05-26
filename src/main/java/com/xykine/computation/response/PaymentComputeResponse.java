package com.xykine.computation.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.xykine.payroll.model.PaymentInfo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class PaymentComputeResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    private UUID id;
    private boolean success;
    private String message;
    private String offCycleId;
    private String fullName;
    private List<PaymentInfo> report;
    private Map<String, BigDecimal> summary;
    private Map<String, List<SummaryDetail>> summaryDetails;
    private String start;
    private String end;
    private boolean payrollSimulation;
    private boolean offCycle;
}

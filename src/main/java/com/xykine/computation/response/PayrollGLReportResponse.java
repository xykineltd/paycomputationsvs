package com.xykine.computation.response;

import com.xykine.computation.dto.GLReportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollGLReportResponse {
    private String reportId;
    private String companyId;
    private String startDate;
    private String endDate;
    private LocalDateTime generated;
    private GLReportStatus status;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;

    @Builder.Default
    private List<PayrollGLLineResponse> lines = new ArrayList<>();
}

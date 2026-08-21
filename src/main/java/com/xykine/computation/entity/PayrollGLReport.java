package com.xykine.computation.entity;

import com.xykine.computation.dto.GLReportStatus;
import com.xykine.computation.dto.GLSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "payroll_gl_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollGLReport {

    @Id
    private String id;

    private String payrollId;

    private LocalDate payrollPeriod;

    private LocalDateTime generated;

    private GLReportStatus status;

    private Map<String, GLSummary> gls;
}

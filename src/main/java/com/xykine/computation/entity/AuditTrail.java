package com.xykine.computation.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.xykine.payroll.model.AuditCategory;
import org.xykine.payroll.model.AuditTrailEvents;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Document
public class AuditTrail {
    @Id
    private String id;
    private String companyId;
    private AuditCategory category;
    private AuditTrailEvents event;
    private String details;
    private String employeeId;
    @CreatedBy
    private String name;
    private LocalDateTime dateTime;
}

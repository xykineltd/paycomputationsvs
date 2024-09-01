package com.xykine.computation.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.xykine.payroll.model.AuditTrailEvents;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class AuditTrailResponse {
    private String companyId;
    private AuditTrailEvents event;
    private String details;
    private String employeeId;
    private String name;
    private String dateTime;
}

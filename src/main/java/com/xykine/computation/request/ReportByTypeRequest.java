package com.xykine.computation.request;

import lombok.Data;
import org.xykine.payroll.model.PayrollCategory;

@Data
public class ReportByTypeRequest {
    String companyId;
    String employeeID;
    String start;
    String end;
    PayrollCategory category;
}

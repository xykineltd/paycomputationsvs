package com.xykine.computation.payrollreconciliation.defaults;

import com.xykine.computation.payrollreconciliation.dto.ColumnMapping;
import com.xykine.computation.payrollreconciliation.dto.EntityAlias;
import com.xykine.computation.payrollreconciliation.dto.Tolerances;
import com.xykine.computation.payrollreconciliation.entity.ReconciliationMapping;

import java.util.ArrayList;
import java.util.List;

public final class DefaultReconciliationMapping {
    private DefaultReconciliationMapping() {}

    public static ReconciliationMapping create(String companyId, List<EntityAlias> aliases) {
        return ReconciliationMapping.builder()
                .companyId(companyId)
                .templateId("moniepoint-july-payroll")
                .headerRowIndex(6)
                .dataStartRow(7)
                .excelMatchKey("EMP ID")
                .systemMatchKey("employeeCode")
                .tolerances(Tolerances.builder().build())
                .entityAliases(aliases != null ? aliases : new ArrayList<>())
                .columnMappings(defaultColumns())
                .status("INCOMPLETE")
                .build();
    }

    private static ColumnMapping col(String excel, String path, String stage, String severity,
                                     boolean enabled, String type, boolean matchKey) {
        return ColumnMapping.builder()
                .excelHeader(excel)
                .systemPath(path)
                .stage(stage)
                .severity(severity)
                .enabled(enabled)
                .valueType(type)
                .isMatchKey(matchKey)
                .build();
    }

    public static List<ColumnMapping> defaultColumns() {
        List<ColumnMapping> cols = new ArrayList<>();
        cols.add(col("EMP ID", "employeeCode", "input", "hard", true, "text", true));
        cols.add(col("EMPLOYEE NAME", "fullName", "input", "soft", true, "text", false));
        cols.add(col("LEGAL ENTITY", "legalEntityName", "input", "hard", true, "text", false));
        cols.add(col("Days worked", "daysWorked", "input", "hard", true, "number", false));
        cols.add(col("PRORATING FACTOR", "proratingFactor", "input", "hard", true, "number", false));
        cols.add(col("UNPAID LEAVES DAYS", "numberOfDaysOfUnpaidAbsence", "input", "hard", true, "number", false));
        cols.add(col("BASIC SALARY", "grossPay.Basic Salary", "input", "hard", true, "money", false));
        cols.add(col("HOUSING", "grossPay.Housing", "input", "hard", true, "money", false));
        cols.add(col("TRANSPORT", "grossPay.Transport", "input", "hard", true, "money", false));
        cols.add(col("OTHER ALLOWANCE", "grossPay.Other Allowance", "input", "hard", true, "money", false));
        cols.add(col("PERFORMANCE BONUS", "grossPay.Performance Bonus", "input", "hard", true, "money", false));
        cols.add(col("ARREARS", "grossPay.Arrears", "input", "hard", true, "money", false));
        cols.add(col("OVERTIME", "grossPay.Overtime", "input", "hard", true, "money", false));
        cols.add(col("GROSS INCOME", "grossPay.Gross Pay", "outcome", "hard", true, "money", false));
        cols.add(col("MONTHLY NHF", "nhf.National Housing Fund", "outcome", "hard", true, "money", false));
        cols.add(col("MONTHLY EMPLOYEE PENSION @ 8%", "pension.Employee Pension Contribution", "outcome", "hard", true, "money", false));
        cols.add(col("PAYE TAX", "payeeTax.PAYE", "outcome", "hard", true, "money", false));
        cols.add(col("TOTAL DEDUCTIONS", "deduction.Total Deduction", "outcome", "hard", true, "money", false));
        cols.add(col("NET SALARY", "netPay", "outcome", "hard", true, "money", false));
        cols.add(col("ER PENSION", "pension.Employer Pension Contribution", "outcome", "hard", true, "money", false));
        return cols;
    }
}

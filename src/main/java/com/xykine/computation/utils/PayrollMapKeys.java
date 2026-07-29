package com.xykine.computation.utils;

/**
 * Canonical keys used in payment / YTD / variance / summary maps.
 */
public final class PayrollMapKeys {
    private PayrollMapKeys() {}

    public static final String PAYE = "PAYE";
    public static final String PAYE_DISPLAY = "Pay-As-You-Earn (PAYE)";
    public static final String TOTAL_PAYE = "Total PAYE";
    public static final String ANNUAL_PAYE_TAX = "ANNUAL PAYE TAX";
    public static final String WHT = "WHT";
    public static final String WHT_DISPLAY = "Total Withholding Tax";
    public static final String GROSS_PAY = "Gross Pay";
    public static final String GROSS_SALARY = "Gross Salary";
    public static final String NET_PAY = "Net Pay";
    public static final String EMPLOYEE_PENSION = "Employee Pension Contribution";
    public static final String EMPLOYER_PENSION = "Employer Pension Contribution";
    public static final String VOLUNTARY_PENSION = "Voluntary Pension Contribution";
    public static final String TOTAL_VOLUNTARY_PENSION = "Total Voluntary Pension Contribution";
    public static final String MONTHLY_CHARGEABLE_INCOME = "MONTHLY CHARGEABLE INCOME";
    public static final String ANNUAL_EMPLOYEE_PENSION_8 = "ANNUAL EMPLOYEE PENSION @ 8%";
    public static final String RENT_RELIEF = "RENT RELIEF";
    public static final String ANNUAL_NHF_ALLOWANCE = "ANNUAL NHF ALLOWANCE";
    public static final String ANNUAL_VOLUNTARY_PENSION = "Annual Voluntary Pension Contribution";
    public static final String MONTHLY_RELIEF = "MONTHLY RELIEF";
    public static final String CALL_DATA_ALLOWANCE = "Call/Data Allowance";

    public static String offCyclePayeKey(String paymentName) {
        return "Paye Tax on " + (paymentName != null ? paymentName : "Off-Cycle");
    }
}

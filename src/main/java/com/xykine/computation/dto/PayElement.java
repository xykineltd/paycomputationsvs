package com.xykine.computation.dto;

public enum PayElement {
    PERFORMANCE_BONUS("PERFORMANCE BONUS"),
    ARREARS("ARREARS"),
    OVERTIME("OVERTIME"),
    OTHER_EARNINGS("OTHER EARNINGS"),
    REPAIR_BONUS("REPAIR BONUS"),
    PILON("PILON"),
    PER_DIEM("PER DIEM"),
    SIGN_ON_BONUS("SIGN ON BONUS"),
    ON_CAL("ON CALL"),
    REFERRAL_BONUS("REFERRAL BONUS"),

    UNPAID_LEAVE("UNPAID LEAVE"),
    NOTICE_PAY_CLAWBACK("NOTICE PAY CLAWBACK"),

    MONTHLY_NHF("MONTHLY NHF"),
    MONTHLY_EMPLOYEE_PENSION_8_PERCENT("MONTHLY EMPLOYEE PENSION @ 8%"),
    MONTHLY_VOLUNTARY_PENSION("MONTHLY VOLUNTARY PENSION"),
    ER_PENSION("ER PENSION"),

    ON_CALL("ON CALL"),
    PAYE_TAX("PAYE TAX"),

    SALARY_ADVANCES("SALARY ADVANCES"),
    STAFF_LOANS("STAFF LOANS"),
    DEVICE_DAMAGE("DEVICE DAMAGE"),
    OTHER_DEDUCTIONS("OTHER DEDUCTIONS"),

    OTHER_NET_PAYMENTS("OTHER NET PAYMENTS"),

    CALL_AND_DATA_ALLOWANCE("CALL & DATA ALLOWANCE"),
    TRAVEL_ALLOWANCE("TRAVEL ALLOWANCE"),

    NET_SALARY("NET SALARY"),

    NSITF("NSITF"),
    ITF("ITF");

    private final String displayName;

    PayElement(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

package com.xykine.computation.model.wagetypegroup;

public enum RecurringPayments {

    FUEL_ALLOWANCE("1001", "Fuel Allowance"),
    CAR_MAINTENANCE_ALLOWANCE("1002", "Car Maintenance Allowance"),
    DRIVER_ALLOWANCE("1003", "Driver Allowance"),
    STEWARD_ALLOWANCE("1004", "Steward Allowance"),
    TRANSPORT_ALLOWANCE("1005", "Transport Allowance"),
    DATA_ALLOWANCE("1021", "Data Allowance"),
    LUNCH_ALLOWANCE("1022", "Lunch Allowance"),
    SECURITY_ALLOWANCE("1101", "Security Allowance");
    private final String code;
    private final String description;

    RecurringPayments(String aCode, String aDescription){
        code = aCode;
        description = aDescription;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}

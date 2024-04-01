package com.xykine.computation.model.enums;

public enum PaymentTypeEnum {
    ALLOWANCE_ANNUAL_TRANSPORT("ANNUAL TRANSPORT ALLOWANCE"),
    ALLOWANCE_ANNUAL_HOUSING("ANNUAL HOUSING ALLOWANCE"),
    ALLOWANCE_ANNUAL("ANNUAL ALLOWANCE"),
    BASIC_SALARY_ANNUAL("ANNUAL BASIC SALARY"),
    ALLOWANCE_MONTHLY("MONTHLY ALLOWANCE"),
    DEDUCTION_ANNUAL("ANNUAL DEDUCTION"),
    DEDUCTION_MONTHLY("MONTHLY DEDUCTION");

    private final String description;

    PaymentTypeEnum(String aDescription){
        description = aDescription;
    }

    public String getDescription() {
        return description;
    }
}

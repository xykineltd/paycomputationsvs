package com.xykine.computation.model;

public enum PersonalCalculationRule {
    HOURLY("1", "Hourly Paid"),
    SALARIES("3", "Salaries");

    private final String pcr;
    private final String description;

    PersonalCalculationRule(String aPcr, String aDescription){
        pcr = aPcr;
        description = aDescription;
    }

}

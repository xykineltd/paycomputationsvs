package com.xykine.computation.model.wagetypegroup;

public enum Earnings {
    BASIC_SALARY("0101-0200", "Basic Salary", "0800"),
    HOURLY_PAID("0201-0300", "Hourly Paid", "0800"),
    OVERTIME("0401-0500", "Overtime", "2010"),
    RECURRING_PAYMENT("1001-2000", "Recurring Payment", "0014"),
    ADDITIONAL_PAYMENT("2001-3000", "Additional Payments", "0015");
    private final String range;
    private final String group;
    private final String infoType;

    Earnings(String aRange, String aGroup, String anInfoType){
      range = aRange;
      group = aGroup;
      infoType = anInfoType;
    }

    public String getRange() {
        return range;
    }

    public String getGroup() {
        return group;
    }

    public String getInfoType() {
        return infoType;
    }
}

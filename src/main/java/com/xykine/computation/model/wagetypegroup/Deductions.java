package com.xykine.computation.model.wagetypegroup;

public enum Deductions {
   RECURRING_DEDUCTIONS("0101-0200", "Recurring Deductions", "0014"),
    ADDITIONAL_DEDUCTIONS("0201-0300", "Additional Deductions", "0015"),
    EXTERNAL_TRANSFER("0401-0500", "External Transfer", "0011"),
    MEMBERSHIP_FEES("1001-2000", "Membership Fees", "0057"),
   INCOME_TAX("", "Income Tax", "001");
    private final String range;
    private final String group;
    private final String infoType;

    Deductions(String aRange, String aGroup, String anInfoType){
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

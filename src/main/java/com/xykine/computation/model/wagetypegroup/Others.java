package com.xykine.computation.model.wagetypegroup;

public enum Others {
    TRAVEL_MANAGEMENT("8000-8200", "Travel Management", "N/A"),
    TAX_CALCULATION("9001-9100", "Tax Calculation", "N/A"),
    EMPLOYER_CONTRIBUTION("9101-9200", "Employer Contribution", "N/A"),
    PROVISIONS("9201-9300", "Provisions", "N/A"),
    RESERVED("9301-9400", "Reserved", "N/A"),
    LEAVE_VALUATION_ABSENCES("9401 - 9500", "Leave valuation with absences", "N/A"),
    LOANS("9501 - 9600", "Loans", "0045"),
    BASE_STAT_RECURRING("9601 - 9650", "Base Statistical recurring", "0014"),
    BASE_STAT_ADDITIONAL("9651 - 9700", "Base Statistical Additional", "0015"),
    BASE_STAT_BASIC_PAY("9701 - 9750", "ABase: Statistical Basic Pay", "0008"),
    BASE_APPLICABLE_AMOUNT("9751 - 9800", "Base Applicable Amount", "N/A"),
    UPFRONT_AMORTIZES("9A00 – 9A99", "Upfront – Amortized", "N/A"),
    UPFRONT_PAID_YTP("9B00 – 9B99", "Upfront - Balances", "N/A"),
    UPFRONT_BALANCES("9C00 - 9C99", "Upfront – Paid YTD", "N/A"),
    BASE_STAT_BASE_AMT("9D00 – 9D99", "Upfront – Base Amount", "N/A"),
    UPFRONT_WRITE_OFF("9E00 – 9E99", "Upfront – Write-off", "N/A"),;
    private final String range;
    private final String group;
    private final String infoType;

    Others(String aRange, String aGroup, String anInfoType){
        range = aRange;
        group = aGroup;
        infoType = anInfoType;
    }
}

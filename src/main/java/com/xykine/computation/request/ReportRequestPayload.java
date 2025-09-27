package com.xykine.computation.request;

import lombok.Data;

import java.util.List;

@Data
public class ReportRequestPayload {
    private boolean all;
    private List<String> ids;
    private List<String> headers;
    private String companyID;
    private String entityType;
    private DateRange dateRange;
    private String docType;
}

/*
{
  "isAll": true,
  "ids": [],
  "companyID": "68d6276a019e0574895914dd",
  "headers": ["financeCode", "glAccountNumber", "glAccountDescription", "statementType", "amount"],
  "entity": "Finance",
  "entityType": "Regular",
  "dateRange": { "fromDate": "2025-09-01", "endDate": "2025-09-30" },
  "docType": "xcell"
}


#  {
# isAll: Boolean
# Ids: string[],
# Headers: string[]
# Entity: Payroll / Employee/ Finance / FinanceDetail / PayrollDetail,
# EntityType: Regular/ Offcycle / Payslip
# DateRange: { fromDate, endDate }
# docType: pdf /  xcell
# }
* */
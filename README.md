# paycomputationsvs
The microservice responsible for payment calculation


## Success Factor API call

### 

- Endpoint
  `https://sandbox.api.sap.com/successfactors/odata/v2/Background_VarPayEmpHistData?$top=1`
- Response
```
{
  "d": {
    "results": [
      {
        "__metadata": {
          "uri": "https://sandbox.api.sap.com/successfactors/odata/v2/Background_VarPayEmpHistData(442L)",
          "type": "SFOData.Background_VarPayEmpHistData"
        },
        "backgroundElementId": "442",
        "country": "United States",
        "endDate": "/Date(1480377600000)/",
        "lastModifiedDate": "/Date(1441700141000+0000)/",
        "jobTitle": "Professional Services (SVC)",
        "businessGoalCode": "N Amer",
        "basis": "25000",
        "salary": "122500",
        "userId": "802982",
        "division": "Manufacturing (MANU)",
        "tgtPct": "15",
        "payGrade": "GR-19",
        "location": "Boston (3400-0001)",
        "incentivePlanCode": "Mgr",
        "varPayProgramName": 141,
        "department": "Engineering",
        "currencyCode": "USD",
        "startDate": "/Date(1459468800000)/"
      }
    ]
  }
}
```

- Endpoint
  `https://sandbox.api.sap.com/successfactors/odata/v2/Background_VarPayEmpHistData?$top=1`
- Response




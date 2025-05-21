package com.xykine.computation.testdata;

public class TestDataProvider {

    public static String ONE_THOUSAND_ENTRIES = TestDataGenerator.generateEntries(1000);
    public static String TEN_ENTRIES = TestDataGenerator.generateEntries(10);
    public static String STANDARD_PAYROLL_ENTRY  =
            """
                    [
                      {
                        "id": null,
                        "numberOfDaysOfUnpaidAbsence": 0,
                        "startDate": "2025-05-01",
                        "endDate": "2025-05-31",
                        "employeeID": "682cf69592b07e60fa10991b",
                        "companyID": "682cf69492b07e60fa109911",
                        "completed": false,
                        "employeeIsLock": false,
                        "paymentSettings": [
                          {
                            "paymentSettingID": null,
                            "employeeID": "682cf69592b07e60fa10991b",
                            "type": "ALLOWANCE_ANNUAL_TRANSPORT",
                            "name": "Transport Allowance",
                            "value": 50770.89,
                            "currency": "NGN",
                            "salaryFrequency": "MONTHLY",
                            "active": false,
                            "pensionable": false,
                            "prorated": false,
                            "createdDate": null,
                            "lastModifiedDate": null,
                            "createdBy": null,
                            "lastModifiedBy": null,
                            "version": 0
                          },
                          {
                            "paymentSettingID": null,
                            "employeeID": "682cf69592b07e60fa10991b",
                            "type": "ALLOWANCE_ANNUAL",
                            "name": "Acting Allowance",
                            "value": 8219.55,
                            "currency": "NGN",
                            "salaryFrequency": "MONTHLY",
                            "active": false,
                            "pensionable": false,
                            "prorated": false,
                            "createdDate": null,
                            "lastModifiedDate": null,
                            "createdBy": null,
                            "lastModifiedBy": null,
                            "version": 0
                          },
                          {
                            "paymentSettingID": null,
                            "employeeID": "682cf69592b07e60fa10991b",
                            "type": "DEDUCTION_MONTHLY",
                            "name": "Coop Loan",
                            "value": 6031.32,
                            "currency": "NGN",
                            "salaryFrequency": "MONTHLY",
                            "active": false,
                            "pensionable": false,
                            "prorated": false,
                            "createdDate": null,
                            "lastModifiedDate": null,
                            "createdBy": null,
                            "lastModifiedBy": null,
                            "version": 0
                          },
                          {
                            "paymentSettingID": null,
                            "employeeID": "682cf69592b07e60fa10991b",
                            "type": "BASIC_SALARY_ANNUAL",
                            "name": "Basic Salary",
                            "value": 507722.72,
                            "currency": "NGN",
                            "salaryFrequency": "MONTHLY",
                            "active": false,
                            "pensionable": false,
                            "prorated": false,
                            "createdDate": null,
                            "lastModifiedDate": null,
                            "createdBy": null,
                            "lastModifiedBy": null,
                            "version": 0
                          },
                          {
                            "paymentSettingID": null,
                            "employeeID": "682cf69592b07e60fa10991b",
                            "type": "ALLOWANCE_ANNUAL_HOUSING",
                            "name": "Housing Allowance",
                            "value": 86338.91,
                            "currency": "NGN",
                            "salaryFrequency": "MONTHLY",
                            "active": false,
                            "pensionable": false,
                            "prorated": false,
                            "createdDate": null,
                            "lastModifiedDate": null,
                            "createdBy": null,
                            "lastModifiedBy": null,
                            "version": 0
                          }
                        ],
                        "basicSalary": 507722.72,
                        "fullName": "Maudie Steuber",
                        "offCycleID": null,
                        "offCycle": false,
                        "offCycleActualValueSupplied": false,
                        "currency": "NGN",
                        "salaryFrequency": "MONTHLY",
                        "exchangeInfo": {
                          "currency": "NGN",
                          "rateDateAndTime": null,
                          "exchangeRate": 1.0
                        },
                        "totalNumberOfEmployees": 1,
                        "ytdReport": null
                      }
                    ]
            """;

    public static String OFF_CYCLE  =
            """
                    [
                      {
                        "id": "682cf93a92b07e60fa10994e",
                        "numberOfDaysOfUnpaidAbsence": 0,
                        "startDate": "2025-05-20",
                        "endDate": "2025-05-20",
                        "employeeID": "682cf69592b07e60fa10991b",
                        "companyID": "682cf69492b07e60fa109911",
                        "completed": false,
                        "employeeIsLock": false,
                        "paymentSettings": [
                          {
                            "paymentSettingID": null,
                            "employeeID": "682cf69592b07e60fa10991b",
                            "type": "DEDUCTION_MONTHLY",
                            "name": "Coop Loan",
                            "value": 0.0,
                            "currency": "NGN",
                            "salaryFrequency": null,
                            "active": false,
                            "pensionable": false,
                            "prorated": false,
                            "createdDate": null,
                            "lastModifiedDate": null,
                            "createdBy": null,
                            "lastModifiedBy": null,
                            "version": 0
                          },
                          {
                            "paymentSettingID": null,
                            "employeeID": "682cf69592b07e60fa10991b",
                            "type": "ALLOWANCE_ANNUAL_TRANSPORT",
                            "name": "Transport Allowance",
                            "value": 0.0,
                            "currency": "NGN",
                            "salaryFrequency": null,
                            "active": false,
                            "pensionable": false,
                            "prorated": false,
                            "createdDate": null,
                            "lastModifiedDate": null,
                            "createdBy": null,
                            "lastModifiedBy": null,
                            "version": 0
                          },
                          {
                            "paymentSettingID": null,
                            "employeeID": "682cf69592b07e60fa10991b",
                            "type": "OFF_CYCLE_PAYMENT_AMOUNT",
                            "name": "Off-Cycle Payment Amount",
                            "value": 507722.72,
                            "currency": "NGN",
                            "salaryFrequency": null,
                            "active": false,
                            "pensionable": false,
                            "prorated": false,
                            "createdDate": null,
                            "lastModifiedDate": null,
                            "createdBy": null,
                            "lastModifiedBy": null,
                            "version": 0
                          },
                          {
                            "paymentSettingID": null,
                            "employeeID": "682cf69592b07e60fa10991b",
                            "type": "ALLOWANCE_ANNUAL",
                            "name": "Acting Allowance",
                            "value": 0.0,
                            "currency": "NGN",
                            "salaryFrequency": null,
                            "active": false,
                            "pensionable": false,
                            "prorated": false,
                            "createdDate": null,
                            "lastModifiedDate": null,
                            "createdBy": null,
                            "lastModifiedBy": null,
                            "version": 0
                          },
                          {
                            "paymentSettingID": null,
                            "employeeID": "682cf69592b07e60fa10991b",
                            "type": "BASIC_SALARY_ANNUAL",
                            "name": "Basic Salary",
                            "value": 0.0,
                            "currency": "NGN",
                            "salaryFrequency": null,
                            "active": false,
                            "pensionable": false,
                            "prorated": false,
                            "createdDate": null,
                            "lastModifiedDate": null,
                            "createdBy": null,
                            "lastModifiedBy": null,
                            "version": 0
                          },
                          {
                            "paymentSettingID": null,
                            "employeeID": "682cf69592b07e60fa10991b",
                            "type": "ALLOWANCE_ANNUAL_HOUSING",
                            "name": "Housing Allowance",
                            "value": 0.0,
                            "currency": "NGN",
                            "salaryFrequency": null,
                            "active": false,
                            "pensionable": false,
                            "prorated": false,
                            "createdDate": null,
                            "lastModifiedDate": null,
                            "createdBy": null,
                            "lastModifiedBy": null,
                            "version": 0
                          }
                        ],
                        "basicSalary": 507722.72,
                        "fullName": "Maudie Steuber",
                        "offCycleID": "682cf93a92b07e60fa10994d",
                        "offCycle": true,
                        "offCycleActualValueSupplied": false,
                        "currency": "NGN",
                        "salaryFrequency": null,
                        "exchangeInfo": {
                          "currency": "NGN",
                          "rateDateAndTime": null,
                          "exchangeRate": 1.0
                        },
                        "totalNumberOfEmployees": 1,
                        "ytdReport": null
                      }
                    ]               
            """;
}

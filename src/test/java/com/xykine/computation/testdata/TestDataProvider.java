package com.xykine.computation.testdata;

public class TestDataProvider {

    public static String ONE_THOUSAND_ENTRIES = TestDataGenerator.generateEntries(1000);
    public static String TEN_ENTRIES = TestDataGenerator.generateEntries(10);
    public static String STANDARD_PAYROLL_ENTRY_WITH_PERFORMANCE_BONUS  =
            """
                   [
                     {
                                 "id": null,
                                 "numberOfDaysOfUnpaidAbsence": 2,
                                 "startDate": "2025-06-01",
                                 "endDate": "2025-06-30",
                                 "employeeID": "682cf69592b07e60fa10991b",
                                 "companyID": "682cf69492b07e60fa109911",
                                 "completed": false,
                                 "employeeIsLock": false,
                                 "paymentSettings": [
                                   {
                                     "paymentSettingID": null,
                                     "employeeID": "682cf69592b07e60fa10991b",
                                     "type": "BASIC_SALARY_ANNUAL",
                                     "name": "Basic Salary",
                                     "value":   1703911.85,
                                     "currency": "NGN",
                                     "salaryFrequency": "YEARLY",
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
                                     "value":   851955.92,
                                     "currency": "NGN",
                                     "salaryFrequency": "YEARLY",
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
                                     "value": 851955.92,
                                     "currency": "NGN",
                                     "salaryFrequency": "YEARLY",
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
                                     "name": "Utility",
                                     "value": 1035183.38,
                                     "currency": "NGN",
                                     "salaryFrequency": "YEARLY",
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
                                     "name": "Entertainment",
                                     "value": 1035183.38,
                                     "currency": "NGN",
                                     "salaryFrequency": "YEARLY",
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
                                     "name": "Medical",
                                     "value": 1035183.38,
                                     "currency": "NGN",
                                     "salaryFrequency": "YEARLY",
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
                                     "name": "Leave",
                                     "value": 1035183.38,
                                     "currency": "NGN",
                                     "salaryFrequency": "YEARLY",
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
                                     "name": "Training",
                                     "value": 1035183.38,
                                     "currency": "NGN",
                                     "salaryFrequency": "YEARLY",
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
                                     "name": "PERSONAL OUTFIT",
                                     "value":  1768093.22,
                                     "currency": "NGN",
                                     "salaryFrequency": "YEARLY",
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
                                     "name": "MONTHLY PERFORMANCE BONUS",
                                     "value":  15,
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
                                     "type": "OFF_CYCLE_PAYMENT_AMOUNT",
                                     "name": "OVERTIME GROSS",
                                     "value":  58817.24,
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
                                 "basicSalary": 10351833.82,
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

    public static String STANDARD_PAYROLL_ENTRY =
            """
                   [
                     {
                                 "id": null,
                                 "numberOfDaysOfUnpaidAbsence": 2,
                                 "startDate": "2025-06-01",
                                 "endDate": "2025-06-30",
                                 "employeeID": "682cf69592b07e60fa10991b",
                                 "companyID": "682cf69492b07e60fa109911",
                                 "completed": false,
                                 "employeeIsLock": false,
                                 "paymentSettings": [
                                   {
                                     "paymentSettingID": null,
                                     "employeeID": "682cf69592b07e60fa10991b",
                                     "type": "BASIC_SALARY_ANNUAL",
                                     "name": "Basic Salary",
                                     "value":   1703911.85,
                                     "currency": "NGN",
                                     "salaryFrequency": "YEARLY",
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
                                     "value":   851955.92,
                                     "currency": "NGN",
                                     "salaryFrequency": "YEARLY",
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
                                     "value": 851955.92,
                                     "currency": "NGN",
                                     "salaryFrequency": "YEARLY",
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
                                     "name": "Utility",
                                     "value": 1035183.38,
                                     "currency": "NGN",
                                     "salaryFrequency": "YEARLY",
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
                                     "name": "Entertainment",
                                     "value": 1035183.38,
                                     "currency": "NGN",
                                     "salaryFrequency": "YEARLY",
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
                                     "name": "Medical",
                                     "value": 1035183.38,
                                     "currency": "NGN",
                                     "salaryFrequency": "YEARLY",
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
                                     "name": "Leave",
                                     "value": 1035183.38,
                                     "currency": "NGN",
                                     "salaryFrequency": "YEARLY",
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
                                     "name": "Training",
                                     "value": 1035183.38,
                                     "currency": "NGN",
                                     "salaryFrequency": "YEARLY",
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
                                     "name": "PERSONAL OUTFIT",
                                     "value":  1768093.22,
                                     "currency": "NGN",
                                     "salaryFrequency": "YEARLY",
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
                                 "basicSalary": 10351833.82,
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

    public static String STANDARD_PAYROLL_ENTRY_WITH_PAYMENT_DISTRIBUTION_LIST =
            """
                   [
                     {
                                 "id": null,
                                 "numberOfDaysOfUnpaidAbsence": 2,
                                 "startDate": "2025-06-01",
                                 "endDate": "2025-06-30",
                                 "employeeID": "7654321",
                                 "companyID": "1234567",
                                 "completed": false,
                                 "employeeIsLock": false,
                                 "basicSalary": 10351833.82,
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

    public static String STANDARD_PAYROLL_ENTRY_WITH_PERFORMANCE_BONUS_DISTRIBUTION_LIST  =
            """
                   [
                     {
                                 "id": null,
                                 "numberOfDaysOfUnpaidAbsence": 2,
                                 "startDate": "2025-06-01",
                                 "endDate": "2025-06-30",
                                 "employeeID": "7654321",
                                 "companyID": "1234567",
                                 "completed": false,
                                 "employeeIsLock": false,
                                 "paymentSettings": [
                                   {
                                     "paymentSettingID": null,
                                     "employeeID": "682cf69592b07e60fa10991b",
                                     "type": "OFF_CYCLE_PAYMENT_AMOUNT",
                                     "name": "MONTHLY PERFORMANCE BONUS",
                                     "value":  15,
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
                                     "type": "OFF_CYCLE_PAYMENT_AMOUNT",
                                     "name": "OVERTIME GROSS",
                                     "value":  58817.24,
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
                                 "basicSalary": 10351833.82,
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

    public static String STANDARD_PAYROLL_ENTRY_WITH_PERFORMANCE_BONUS_DISTRIBUTION_LIST_CUSTOM_TAX_RELEIF  =
            """
                   [
                     {
                                 "id": null,
                                 "numberOfDaysOfUnpaidAbsence": 2,
                                 "startDate": "2025-06-01",
                                 "endDate": "2025-06-30",
                                 "employeeID": "8654321",
                                 "companyID": "1234567",
                                 "completed": false,
                                 "employeeIsLock": false,
                                 "paymentSettings": [
                                   {
                                     "paymentSettingID": null,
                                     "employeeID": "682cf69592b07e60fa10991b",
                                     "type": "OFF_CYCLE_PAYMENT_AMOUNT",
                                     "name": "MONTHLY PERFORMANCE BONUS",
                                     "value":  15,
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
                                     "type": "OFF_CYCLE_PAYMENT_AMOUNT",
                                     "name": "OVERTIME GROSS",
                                     "value":  58817.24,
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
                                 "basicSalary": 10351833.82,
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
                            "salaryFrequency": "YEARLY",
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

    public static String CONTRACT_STAFF =
            """
                    [
                      {
                        "id": null,
                        "numberOfDaysOfUnpaidAbsence": 0,
                        "startDate": "2025-05-01",
                        "endDate": "2025-05-31",
                        "employeeID": "8e3b6e4952e8468a84fd84556f8fdf2a",
                        "companyID": "682cf69492b07e60fa109911",
                        "completed": false,
                        "employeeIsLock": false,
                        "paymentSettings": [
                          {
                            "paymentSettingID": null,
                            "employeeID": "8e3b6e4952e8468a84fd84556f8fdf2a",
                            "type": "BASIC_SALARY_ANNUAL",
                            "name": "Basic Salary",
                            "value": 1800000.00,
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
                        "basicSalary": 1800000.00,
                        "fullName": "Ferondo Redondo",
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
}

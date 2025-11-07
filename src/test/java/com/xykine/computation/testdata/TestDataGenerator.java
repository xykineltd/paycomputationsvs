package com.xykine.computation.testdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TestDataGenerator {

    public static String generateEntries(int numberOfEntries) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode rootArray = mapper.createArrayNode();

        for (int i = 1; i <= numberOfEntries; i++) {
            ObjectNode entry = mapper.createObjectNode();
            entry.put("id", (String) null);
            entry.put("numberOfDaysOfUnpaidAbsence", 0);
            entry.put("startDate", "2025-05-01");
            entry.put("endDate", "2025-05-31");
            entry.put("employeeID", "emp-" + String.format("%04d", i));
            entry.put("companyID", "1234567");
            entry.put("completed", false);
            entry.put("employeeIsLock", false);
            entry.put("basicSalary", 10351833.82);
            entry.put("fullName", "Employee " + i);
            entry.put("offCycleID", (String) null);
            entry.put("offCycle", false);
            entry.put("offCycleActualValueSupplied", false);
            entry.put("currency", "NGN");
            entry.put("salaryFrequency", "MONTHLY");
            ObjectNode exchangeInfo = mapper.createObjectNode();
            exchangeInfo.put("currency", "NGN");
            exchangeInfo.put("rateDateAndTime", (String) null);
            exchangeInfo.put("exchangeRate", 1.0);
            entry.set("exchangeInfo", exchangeInfo);
            entry.put("totalNumberOfEmployees", 100);
            entry.put("ytdReport", (String) null);
            rootArray.add(entry);
        }
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootArray);
        } catch (Exception e) {
            throw new RuntimeException("Error generating test data", e);
        }
    }

    public static String getTestDataForCostCenterTest() {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode rootArray = mapper.createArrayNode();
        for (int i = 1; i <= 10; i++) {
            ObjectNode entry = mapper.createObjectNode();
            entry.put("id", (String) null);
            entry.put("numberOfDaysOfUnpaidAbsence", 0);
            entry.put("startDate", "2025-05-01");
            entry.put("endDate", "2025-05-31");
            entry.put("employeeID", "emp-" + String.format("%04d", i));
            entry.put("companyID", "1234567");
            entry.put("completed", false);
            entry.put("employeeIsLock", false);
            entry.put("basicSalary", 10351833.82);
            entry.put("fullName", "Employee " + i);
            entry.put("offCycleID", (String) null);
            entry.put("offCycle", false);
            entry.put("offCycleActualValueSupplied", false);
            entry.put("currency", "NGN");
            entry.put("salaryFrequency", "MONTHLY");
            ObjectNode exchangeInfo = mapper.createObjectNode();
            exchangeInfo.put("currency", "NGN");
            exchangeInfo.put("rateDateAndTime", (String) null);
            exchangeInfo.put("exchangeRate", 1.0);
            entry.set("exchangeInfo", exchangeInfo);
            entry.put("totalNumberOfEmployees", 100);
            entry.put("ytdReport", (String) null);
            rootArray.add(entry);
        }

        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootArray);
        } catch (Exception e) {
            throw new RuntimeException("Error generating test data", e);
        }
    }

    public static Map<String, List<String>> getCostCenterDetails() {
        Map<String, List<String>> costCenterMap = new HashMap<>();
        List<String> employeesInA = new ArrayList<>();
        employeesInA.add("emp-0001");
        employeesInA.add("emp-0002");
        employeesInA.add("emp-0003");

        List<String> employeesInB = new ArrayList<>();
        employeesInB.add("emp-0004");
        employeesInB.add("emp-0005");
        employeesInB.add("emp-0006");
        employeesInB.add("emp-0007");
        employeesInB.add("emp-0008");
        employeesInB.add("emp-0009");
        employeesInB.add("emp-0010");
        costCenterMap.put("costCenterA", employeesInA);
        costCenterMap.put("costCenterB", employeesInB);
        return costCenterMap;
    }

    private static ArrayNode generatePaymentSettings(ObjectMapper mapper, int index) {
        ArrayNode settingsArray = mapper.createArrayNode();

        if (index % 5 != 0) {
            settingsArray.add(createPaymentSetting(mapper, index, "BASIC_SALARY_ANNUAL", "Basic Salary", 500000.0 + (index % 5) * 10000));
        }

        if (index % 3 == 0) {
            settingsArray.add(createPaymentSetting(mapper, index, "ALLOWANCE_ANNUAL_TRANSPORT", "Transport Allowance", 50000.0));
        }

        if (index % 4 == 0) {
            settingsArray.add(createPaymentSetting(mapper, index, "ALLOWANCE_ANNUAL_HOUSING", "Housing Allowance", 80000.0));
        }

        return settingsArray;
    }

    private static ObjectNode createPaymentSetting(ObjectMapper mapper, int index, String type, String name, double value) {
        ObjectNode setting = mapper.createObjectNode();
        setting.put("paymentSettingID", (String) null);
        setting.put("employeeID", "emp-" + String.format("%04d", index));
        setting.put("type", type);
        setting.put("name", name);
        setting.put("value", value);
        setting.put("currency", "NGN");
        setting.put("salaryFrequency", "MONTHLY");
        setting.put("active", false);
        setting.put("pensionable", false);
        setting.put("prorated", false);
        setting.put("createdDate", (String) null);
        setting.put("lastModifiedDate", (String) null);
        setting.put("createdBy", (String) null);
        setting.put("lastModifiedBy", (String) null);
        setting.put("version", 0);
        return setting;
    }
}


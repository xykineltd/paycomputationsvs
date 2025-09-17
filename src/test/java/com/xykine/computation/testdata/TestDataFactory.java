package com.xykine.computation.testdata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import static com.xykine.computation.testdata.TestDataProvider.*;

public class TestDataFactory {
    private static ObjectMapper MAPPER = new ObjectMapper();
    public static final String TEST_COMPANY_ID = "682cf69492b07e60fa109911";
    public static final String TEST_EMPLOYEE_ID = "682cf69592b07e60fa10991b";

    public static <T> List<T> getPaymentSettings(String type) {
        String payload = switch (type) {
            case "standard" -> STANDARD_PAYROLL_ENTRY;
            case "contract staff" -> CONTRACT_STAFF;
            case "standard with performance bonus" -> STANDARD_PAYROLL_ENTRY_WITH_PERFORMANCE_BONUS;
            case "off-cycle" -> OFF_CYCLE;
            case "ten-entries" -> TEN_ENTRIES;
            case "one-thousand-entries" -> ONE_THOUSAND_ENTRIES;
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
        try {
            return MAPPER.readValue(payload, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse payment info list", e);
        }
    }
}
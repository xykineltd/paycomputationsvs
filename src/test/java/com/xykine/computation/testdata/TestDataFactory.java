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
        String payload = "";
        if ("standard".equals(type)) {
            payload = STANDARD_PAYROLL_ENTRY;
        } else if ("off-cycle".equals(type)) {
            payload = OFF_CYCLE;
        } else if ("ten-entries".equals(type)) {
            payload = TEN_ENTRIES;
        } else if ("one-thousand-entries".equals(type)) {
            payload = ONE_THOUSAND_ENTRIES;
        }
        try {
            return MAPPER.readValue(payload, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse payment info list", e);
        }
    }
}

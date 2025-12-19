package com.xykine.computation.utils;

import com.xykine.computation.request.EmployeeFilterRequest;

public class AppUtil {

    public static boolean hasAdditionalFilters(EmployeeFilterRequest req) {
        if (req == null) return false;

        // These four are the baseline required ones
        boolean hasOnlyBaseFields =
                isBlank(req.getFirstName()) &&
                        isBlank(req.getLastName()) &&
                        isBlank(req.getDepartmentID()) &&
                        isBlank(req.getPosition()) &&
                        (req.getStartDate() == null) &&
                        isBlank(req.getEmployeeIsLocked()) &&
                        isBlank(req.getActive()) &&
                        (req.getRoles() == null || req.getRoles().isEmpty()) &&
                        (req.getStartDateRange() == null) &&
                        (req.getEndDateRange() == null) &&
                        isBlank(req.getEmail()) &&
                        isBlank(req.getHeader()) &&
                        (req.getEmployeeIds() == null || req.getEmployeeIds().isEmpty()) &&
                        isBlank(req.getPhoneNumber());

        // If all the others are blank/empty, return false (means no additional filters)
        // Otherwise, return true (some filter applied beyond companyId/reportId/page/size)
        return !hasOnlyBaseFields;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

}

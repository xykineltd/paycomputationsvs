package com.xykine.computation.utils;

import com.xykine.computation.config.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;


public class AuthUtility {
    public static CustomUserDetails getUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            return (CustomUserDetails) authentication.getPrincipal();
        }

        return null; // or throw custom UnauthorizedException
    }

    public static String getCurrentUsername() {
        CustomUserDetails user = getUser();
        return (user != null) ? user.getUsername() : null;
    }

    public static String getCurrentEmail() {
        CustomUserDetails user = getUser();
        return (user != null) ? user.getEmail() : null;
    }

    public static String getUserName() {
        CustomUserDetails user = getUser();
        return (user != null) ? user.getUsername() : null;
    }

    public static String getUserEmail() {
        CustomUserDetails user = getUser();
        return (user != null) ? user.getEmail() : null;
    }

    public static String getCompanyId() {
        CustomUserDetails user = getUser();
        return (user != null) ? (String)user.getCustomAttribute("CompanyID") : null;
    }

    public static String getCurrentUser() {
        CustomUserDetails user = getUser();
        return (user != null) ? (String)user.getCustomAttribute("EmployeeID") : null;
    }

    public static String getCurrentTenantId() {
        CustomUserDetails user = getUser();
        return (user != null) ? (String)user.getCustomAttribute("TenantID") : null;
    }
}

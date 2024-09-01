package com.xykine.computation.utils;

import org.springframework.security.core.context.SecurityContextHolder;

public class AuthUtil {
    public static String getCurrentUser(){
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
    public static String getCompanyId(){
        return "test-companyId";
    }
}

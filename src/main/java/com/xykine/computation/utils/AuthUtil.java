package com.xykine.computation.utils;
//import org.springframework.security.core.context.SecurityContextHolder;

import com.xykine.computation.config.CustomUserDetails;
import org.springframework.security.core.context.SecurityContextHolder;

public class AuthUtil {
    static CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

    public static String getCurrentUser(){
        return (String) userDetails.getCustomAttribute("EmployeeID");
    }
    public static String getCompanyId(){
        return (String) userDetails.getCustomAttribute("CompanyID");
    }

    public static String getUserName(){
        return userDetails.getUsername();
    }
}

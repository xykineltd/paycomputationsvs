package com.xykine.computation.utils;

import com.xykine.computation.config.CustomUserDetails;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

public class AuthUtil {

    /** Returns the current Authentication (reactive- or servlet-based). */
    public static Mono<Authentication> getAuthentication() {
        // Try reactive first
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                // Fallback to traditional ThreadLocal context
                .switchIfEmpty(Mono.fromCallable(() -> SecurityContextHolder.getContext().getAuthentication()))
                .filter(auth -> auth != null);
    }

    /** Returns the current CustomUserDetails if available. */
    public static Mono<CustomUserDetails> getCurrentUserDetails() {
        return getAuthentication()
                .filter(auth -> auth.getPrincipal() instanceof CustomUserDetails)
                .map(auth -> (CustomUserDetails) auth.getPrincipal());
    }

    /** Convenience wrappers (reactive). */
    public static Mono<String> getCurrentUserId() {
        return getCurrentUserDetails()
                .map(u -> (String) u.getCustomAttribute("EmployeeID"));
    }

    public static Mono<String> getCompanyId() {
        return getCurrentUserDetails()
                .map(u -> (String) u.getCustomAttribute("CompanyID"));
    }

    public static Mono<String> getUserName() {
        return getCurrentUserDetails()
                .map(CustomUserDetails::getUsername);
    }

    public static Mono<String> getUserEmail() {
        return getCurrentUserDetails()
                .map(CustomUserDetails::getEmail);
    }
}

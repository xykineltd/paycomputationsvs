package com.xykine.computation.utils;

import com.xykine.computation.exceptions.CompanyAccessDeniedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Ensures the authenticated JWT company matches the company scoped by the request.
 */
@Component
public class CompanyAccessGuard {

    private final boolean enforceCompanyAccess;

    public CompanyAccessGuard(
            @Value("${xykine.security.enforce-company-access:true}") boolean enforceCompanyAccess) {
        this.enforceCompanyAccess = enforceCompanyAccess;
    }

    public void requireCompanyAccess(String requestCompanyId) {
        if (!enforceCompanyAccess) {
            return;
        }
        if (!StringUtils.hasText(requestCompanyId)) {
            throw new CompanyAccessDeniedException("companyId is required");
        }
        String jwtCompanyId = AuthUtility.getCompanyId();
        if (!StringUtils.hasText(jwtCompanyId)) {
            throw new CompanyAccessDeniedException("Authenticated companyId is missing from token");
        }
        if (!jwtCompanyId.equals(requestCompanyId)) {
            throw new CompanyAccessDeniedException(
                    "Access denied: authenticated company does not match requested companyId");
        }
    }

    public String requireAuthenticatedCompanyId() {
        if (!enforceCompanyAccess) {
            String jwtCompanyId = AuthUtility.getCompanyId();
            return StringUtils.hasText(jwtCompanyId) ? jwtCompanyId : null;
        }
        String jwtCompanyId = AuthUtility.getCompanyId();
        if (!StringUtils.hasText(jwtCompanyId)) {
            throw new CompanyAccessDeniedException("Authenticated companyId is missing from token");
        }
        return jwtCompanyId;
    }
}

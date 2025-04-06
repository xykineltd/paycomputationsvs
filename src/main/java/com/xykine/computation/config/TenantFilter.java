package com.xykine.computation.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TenantFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(TenantFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws java.io.IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String tenantId = req.getHeader("X-Tenant-ID");
        logger.info("Processing request for tenant ID: {}", tenantId);
        TenantContext.setTenantId(tenantId);
        logger.info("Tenant ID set to: {}", tenantId);
        try {
            chain.doFilter(request, response);
        } catch (Exception e) {
            logger.error("Error occurred while processing request for tenant ID: {}", tenantId, e);
            throw e;
        } finally {
            TenantContext.clear();
            logger.info("Tenant ID cleared after processing request.");
        }
    }
}

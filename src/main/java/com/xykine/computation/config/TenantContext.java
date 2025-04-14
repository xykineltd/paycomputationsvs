package com.xykine.computation.config;

public class TenantContext {
    private static final ThreadLocal<String> tenantId = new ThreadLocal<>();

    public static void setTenantId(String id) {
        tenantId.set(id != null ? id : "aced");
    }

    public static String getTenantId() {
        return tenantId.get() != null ? tenantId.get() : "aced";
    }

    public static void clear() {
        tenantId.remove();
    }
}

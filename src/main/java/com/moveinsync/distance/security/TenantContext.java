package com.moveinsync.distance.security;

/**
 * Thread-local context maintaining tenant identity and active role for the current request.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String tenantId, String role) {
        CURRENT_TENANT.set(tenantId);
        CURRENT_ROLE.set(role);
    }

    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static String getRole() {
        return CURRENT_ROLE.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_ROLE.remove();
    }
}

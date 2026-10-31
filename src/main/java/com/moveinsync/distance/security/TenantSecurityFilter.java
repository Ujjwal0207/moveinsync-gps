package com.moveinsync.distance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Enforces tenant identification and role authorization on every incoming request.
 */
public class TenantSecurityFilter extends OncePerRequestFilter {

    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_USER_ID = "X-User-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String tenantId = request.getHeader(HEADER_TENANT_ID);
        String role = request.getHeader(HEADER_USER_ROLE);
        String userId = request.getHeader(HEADER_USER_ID);

        // Fallback for public health / actuator endpoints
        if (request.getRequestURI().startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (tenantId == null || tenantId.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"MISSING_TENANT_HEADER\", \"message\": \"Header X-Tenant-Id is required for tenant isolation\"}");
            return;
        }

        String effectiveRole = (role != null && !role.trim().isEmpty()) ? role.trim().toUpperCase() : "OPERATOR";
        String effectiveUser = (userId != null && !userId.trim().isEmpty()) ? userId.trim() : "anonymous-user";

        TenantContext.set(tenantId.trim(), effectiveRole);

        try {
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + effectiveRole));
            var auth = new UsernamePasswordAuthenticationToken(effectiveUser, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}

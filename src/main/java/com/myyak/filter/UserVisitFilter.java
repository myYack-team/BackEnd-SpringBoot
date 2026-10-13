package com.myyak.filter;

import com.myyak.service.visitService.VisitTrackingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UserVisitFilter extends OncePerRequestFilter {
    private final VisitTrackingService tracking;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/api/") || path.startsWith("/api/auth/")
                || AdminSessionFilter.isAdminRequest(request)
                || path.equals("/api/error-log") || path.startsWith("/api/error-log/")
                || path.equals("/api/health") || path.startsWith("/api/health/")
                || Set.of("HEAD", "OPTIONS").contains(request.getMethod())
                || (request.getMethod().equals("DELETE") && path.equals("/api/users/me"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        if (request.isAsyncStarted() || response.getStatus() < 200 || response.getStatus() >= 300) return;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Long userId
                && authentication.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_USER"))) {
            tracking.recordVisit(userId);
        }
    }
}

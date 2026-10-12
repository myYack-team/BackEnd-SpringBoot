package com.myyak.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myyak.apiPayload.ApiResponse;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.config.AdminAccessProperties;
import com.myyak.service.adminAuthService.AdminAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AdminSessionFilter extends OncePerRequestFilter {
    public static final String COOKIE = "myyak_admin_session";
    public static final String CSRF_ATTRIBUTE = "admin.csrf";
    private final AdminAuthService service;
    private final AdminAccessProperties properties;
    private final ObjectMapper mapper;

    public static boolean isAdminRequest(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals("/admin") || path.startsWith("/admin/") || path.startsWith("/api/admin/")
                || path.startsWith("/api/drugs/batch/") || path.equals("/api/drugs/batch")
                || (request.getMethod().equals("GET") && (path.equals("/api/error-log") || path.startsWith("/api/error-log/")));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) { return !isAdminRequest(request); }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.equals(properties.getOrigin())) {
            reject(response, 403, "ADMIN403", "허용되지 않은 요청 출처입니다."); return;
        }
        String path = request.getServletPath();
        boolean login = Set.of("/api/admin/auth/request-code", "/api/admin/auth/verify").contains(path);
        boolean write = !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
        if (login && write && !"1".equals(request.getHeader("X-Admin-Request"))) {
            reject(response, 403, "ADMIN403", "올바른 관리자 화면에서 요청해주세요."); return;
        }
        String csrf;
        try {
            csrf = service.findSession(getSessionToken(request));
        } catch (GeneralException e) {
            reject(response, 503, "ADMIN503", "관리자 인증 서비스를 사용할 수 없습니다."); return;
        }
        if (csrf != null) {
                if (write && !login && !equal(csrf, request.getHeader("X-Admin-CSRF"))) {
                    reject(response, 403, "ADMIN403", "요청 검증에 실패했습니다. 화면을 새로고침해주세요."); return;
                }
                request.setAttribute(CSRF_ATTRIBUTE, csrf);
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        "administrator", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        }
        chain.doFilter(request, response);
    }

    public static String getSessionToken(HttpServletRequest request) {
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) {
            if (COOKIE.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private static boolean equal(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), ApiResponse.onFailure(code, message, null));
    }
}

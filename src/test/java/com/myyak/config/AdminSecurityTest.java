package com.myyak.config;

import com.myyak.filter.AdminSessionFilter;
import com.myyak.filter.JwtAuthenticationFilter;
import com.myyak.filter.UserVisitFilter;
import com.myyak.service.visitService.VisitTrackingService;
import com.myyak.service.adminAuthService.AdminAuthService;
import com.myyak.util.JwtProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminSecurityTest.Probe.class)
@Import({SecurityConfig.class, AdminSessionFilter.class, JwtAuthenticationFilter.class, UserVisitFilter.class,
        AdminAccessProperties.class, AdminSecurityTest.TestCors.class, AdminSecurityTest.Probe.class})
class AdminSecurityTest {
    // MockMvc does not populate servletPath by default; Tomcat's default servlet mapping does.
    private static MockHttpServletRequestBuilder get(String path) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).servletPath(path);
    }
    private static MockHttpServletRequestBuilder post(String path) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).servletPath(path);
    }
    @Autowired MockMvc mvc;
    @MockitoBean AdminAuthService service;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean VisitTrackingService tracking;
    private final Cookie cookie = new Cookie(AdminSessionFilter.COOKIE, "valid-session");

    @BeforeEach void setup() {
        when(service.findSession("valid-session")).thenReturn("csrf-token");
        when(jwtProvider.validateToken("user-token")).thenReturn(true);
        when(jwtProvider.getTokenType("user-token")).thenReturn("access");
        when(jwtProvider.getUserIdFromToken("user-token")).thenReturn(7L);
    }

    @Test void anonymousPagesRedirectAndApisRequireAuthentication() throws Exception {
        mvc.perform(get("/admin/index.html")).andExpect(status().isFound()).andExpect(redirectedUrlPattern("/admin/login.html?next=*"));
        mvc.perform(get("/api/admin/probe")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/error-log/files")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/drugs/batch/probe")).andExpect(status().isUnauthorized());
    }

    @Test void normalUserCannotUseAdministratorApisOrBatchBypass() throws Exception {
        mvc.perform(get("/api/admin/probe").header("Authorization", "Bearer user-token")).andExpect(status().isForbidden());
        mvc.perform(post("/api/drugs/batch/probe").header("Authorization", "Bearer user-token")).andExpect(status().isForbidden());
    }

    @Test void adminCookieRequiresCsrfForWritesAndRejectsForeignOrigin() throws Exception {
        mvc.perform(get("/api/admin/probe").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(post("/api/drugs/batch/probe").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(post("/api/drugs/batch/probe").cookie(cookie).header("X-Admin-CSRF", "csrf-token")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/probe").cookie(cookie).header("Origin", "https://untrusted.example")).andExpect(status().isForbidden());
    }

    @Test void emailCodeRequestsRequireExplicitRequestHeader() throws Exception {
        mvc.perform(post("/api/admin/auth/request-code")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/auth/request-code").header("X-Admin-Request", "1")).andExpect(status().isOk());
    }

    @RestController static class Probe {
        @GetMapping("/api/admin/probe") String getProbe() { return "ok"; }
        @PostMapping({"/api/drugs/batch/probe", "/api/admin/auth/request-code"}) String createProbe() { return "ok"; }
    }
    @TestConfiguration static class TestCors {
        @Bean CorsConfigurationSource corsConfigurationSource() {
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", new CorsConfiguration().applyPermitDefaultValues());
            return source;
        }
    }
}

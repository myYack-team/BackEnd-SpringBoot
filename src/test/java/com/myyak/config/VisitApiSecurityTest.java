package com.myyak.config;

import com.myyak.filter.AdminSessionFilter;
import com.myyak.filter.JwtAuthenticationFilter;
import com.myyak.filter.UserVisitFilter;
import com.myyak.service.visitService.VisitTrackingService;
import com.myyak.apiPayload.ApiResponse;
import com.myyak.service.adminAuthService.AdminAuthService;
import com.myyak.service.visitService.VisitService;
import com.myyak.util.JwtProvider;
import com.myyak.web.controller.AdminVisitController;
import com.myyak.web.dto.VisitDTO.VisitResponseDTO;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AdminVisitController.class, VisitApiSecurityTest.UserProbe.class})
@Import({SecurityConfig.class, AdminSessionFilter.class, JwtAuthenticationFilter.class, UserVisitFilter.class,
        AdminAccessProperties.class, AdminSecurityTest.TestCors.class, VisitApiSecurityTest.UserProbe.class})
class VisitApiSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminAuthService admin;
    @MockitoBean JwtProvider jwt;
    @MockitoBean VisitService visits;
    @MockitoBean VisitTrackingService tracking;
    final LocalDate today = LocalDate.of(2026, 10, 13);

    @BeforeEach void setup() {
        when(admin.findSession("valid-session")).thenReturn("csrf-token");
        when(jwt.validateToken("user-token")).thenReturn(true);
        when(jwt.getTokenType("user-token")).thenReturn("access");
        when(jwt.getUserIdFromToken("user-token")).thenReturn(7L);
        when(visits.getVisitSummary()).thenReturn(new VisitResponseDTO.Summary(today, "Asia/Seoul", 2, 1,
                List.of(new VisitResponseDTO.Day(today, 2))));
    }

    @Test void anonymousCannotUseUserApisOrReadAndNormalUserCannotReadAdminStats() throws Exception {
        mvc.perform(get("/api/users/me").servletPath("/api/users/me"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/visits").servletPath("/api/admin/visits"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/visits").servletPath("/api/admin/visits")
                .header("Authorization", "Bearer user-token")).andExpect(status().isForbidden());
        verifyNoInteractions(visits, tracking);
    }

    @Test void existingAppRequestRecordsTheAuthenticatedAccountWithoutAVisitEvent() throws Exception {
        mvc.perform(get("/api/users/me").servletPath("/api/users/me")
                .header("Authorization", "Bearer user-token").param("userId", "999"))
                .andExpect(status().isOk());
        verify(tracking).recordVisit(7L);
        verifyNoMoreInteractions(tracking);
        verifyNoInteractions(visits);
    }

    @Test void adminSessionCanReadAggregateOnlyWithoutCaching() throws Exception {
        mvc.perform(get("/api/admin/visits").servletPath("/api/admin/visits")
                .cookie(new Cookie(AdminSessionFilter.COOKIE, "valid-session")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.result.todayVisitors").value(2))
                .andExpect(jsonPath("$.result.daily[0].visitors").value(2))
                .andExpect(jsonPath("$.result.daily[0].userId").doesNotExist());
        verifyNoInteractions(tracking);
    }

    @Test void removedVisitEndpointDoesNotAcceptClientEvents() throws Exception {
        mvc.perform(post("/api/users/me/visits").servletPath("/api/users/me/visits")
                .header("Authorization", "Bearer user-token"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(visits, tracking);
    }

    @RestController static class UserProbe {
        @GetMapping("/api/users/me") ApiResponse<?> getMe() {
            return ApiResponse.onSuccess(Map.of("id", 7));
        }
    }
}

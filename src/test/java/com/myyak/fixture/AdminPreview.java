package com.myyak.fixture;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.config.*;
import com.myyak.domain.incident.IncidentSnapshot;
import com.myyak.filter.AdminSessionFilter;
import com.myyak.filter.JwtAuthenticationFilter;
import com.myyak.filter.UserVisitFilter;
import com.myyak.service.visitService.VisitTrackingService;
import com.myyak.service.adminAuthService.AdminAuthService;
import com.myyak.service.incidentService.*;
import com.myyak.util.JwtProvider;
import com.myyak.web.controller.AdminAuthController;
import com.myyak.web.controller.AdminIncidentController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.cors.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.mockito.Mockito.*;

/** Synthetic adapters; actual security chain/controllers/static files. Never a production entrypoint. */
@TestConfiguration
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class})
@Import({SecurityConfig.class, AdminSessionFilter.class, JwtAuthenticationFilter.class, UserVisitFilter.class, AdminAuthController.class,
        AdminIncidentController.class, IncidentServiceImpl.class, AdminPreview.Health.class})
public class AdminPreview {
    public static void main(String[] args) {
        SpringApplication.run(AdminPreview.class, "--server.address=127.0.0.1", "--server.port=8780",
                "--spring.config.location=optional:classpath:/fixture-empty.properties", "--spring.profiles.active=fixture",
                "--jwt.secret=fixture-only-unused-credential", "--jwt.access-token-expiry=60000", "--jwt.refresh-token-expiry=60000",
                "--spring.main.banner-mode=off", "--logging.level.root=WARN");
    }
    @Bean AdminAccessProperties adminAccessProperties() {
        var value = new AdminAccessProperties(); value.setEnabled(true); value.setEmail("admin@example.test");
        value.setSender("sender@example.test"); value.setOrigin("http://127.0.0.1:8780"); value.setSecureCookie(false); return value;
    }
    @Bean IncidentProperties incidentProperties() {
        var value = new IncidentProperties(); value.setEnabled(true); value.setTableName("fixture-only"); return value;
    }
    @Bean JwtProvider jwtProvider() {
        JwtProvider provider = mock(JwtProvider.class);
        when(provider.validateToken("fixture-user-token")).thenReturn(true);
        when(provider.getTokenType("fixture-user-token")).thenReturn("access");
        when(provider.getUserIdFromToken("fixture-user-token")).thenReturn(7L); return provider;
    }
    @Bean VisitTrackingService visitTrackingService() { return mock(VisitTrackingService.class); }
    @Bean CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration(); config.setAllowedOrigins(List.of("http://127.0.0.1:8780"));
        config.setAllowedMethods(List.of("GET", "POST")); config.setAllowedHeaders(List.of("*")); config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**", config); return source;
    }
    @Bean AdminAuthService adminAuthService() {
        return new AdminAuthService() {
            final Set<String> challenges = ConcurrentHashMap.newKeySet(); final Map<String, String> sessions = new ConcurrentHashMap<>();
            public String createChallenge() { String value = UUID.randomUUID().toString().replace("-", "") + "01234567890"; challenges.add(value); return value; }
            public String createSession(String challenge, String code) {
                if (!"111222333".equals(code) || !challenges.remove(challenge)) throw new GeneralException(ErrorStatus.ADMIN_CODE_INVALID);
                String token = UUID.randomUUID().toString(); sessions.put(token, "fixture-csrf"); return token;
            }
            public String findSession(String token) { return token == null ? null : sessions.get(token); }
            public void deleteSession(String token) { if (token != null) sessions.remove(token); }
        };
    }
    @Bean IncidentReadStore incidentReadStore() {
        long now = java.time.Instant.now().getEpochSecond();
        List<IncidentSnapshot> items = List.of(
                incident("12345678-abcd-abcd-abcd-123456789012", "AWAITING_APPROVAL", "java.lang.IllegalStateException", now - 600,
                        "동일한 복약 요청이 겹치는 경우 재고가 두 번 차감되는 상황을 재현했습니다. 잠금 순서를 통일하고 중복 요청 처리 조건을 보완했습니다."),
                incident("22345678-abcd-abcd-abcd-123456789012", "RUNNING", "java.time.DateTimeException", now - 1800, null),
                incident("32345678-abcd-abcd-abcd-123456789012", "RESOLVED", "java.lang.NullPointerException", now - 86400, "누락된 선택 값을 처리하고 회귀 테스트를 통과했습니다."));
        return new IncidentReadStore() {
            public Page getIncidents(String cursor) { return new Page(items, null); }
            public Optional<IncidentSnapshot> findIncident(String id) { return items.stream().filter(item -> item.id().equals(id)).findFirst(); }
        };
    }
    private static IncidentSnapshot incident(String id, String state, String exception, long at, String summary) {
        return new IncidentSnapshot(id, state, at, at + 120, 3, exception, List.of("com.myyak.service.intakeService.IntakeServiceImpl.createIntakeRecords(IntakeServiceImpl.java:81)"),
                "a".repeat(40), "b".repeat(40), summary, "격리 환경에서 회귀 테스트 완료 · 화면 검증용 데이터", null, null, null, null,
                List.of(new IncidentSnapshot.Event(at, "QUEUED"), new IncidentSnapshot.Event(at + 20, "RUNNING"), new IncidentSnapshot.Event(at + 120, state)));
    }
    @RestController static class Health {
        private volatile boolean visitsUnavailable;
        @PostMapping("/api/admin/fixture/visits-failure") ApiResponse<?> updateVisitsFailure(@RequestParam boolean enabled) {
            visitsUnavailable = enabled;
            return ApiResponse.onSuccess(Map.of("enabled", enabled));
        }
        @GetMapping("/api/admin/visits") org.springframework.http.ResponseEntity<?> getVisits() {
            if (visitsUnavailable) return org.springframework.http.ResponseEntity.status(503)
                    .body(ApiResponse.onFailure("ADMIN503", "관리자 서비스를 사용할 수 없습니다.", null));
            var today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
            var daily = java.util.stream.IntStream.rangeClosed(0, 6).mapToObj(index ->
                    Map.of("date", today.minusDays(6 - index).toString(), "visitors", new int[]{3, 0, 8, 5, 6, 7, 12}[index])).toList();
            return org.springframework.http.ResponseEntity.ok(ApiResponse.onSuccess(
                    Map.of("today", today.toString(), "timeZone", "Asia/Seoul",
                    "todayVisitors", 12, "yesterdayVisitors", 7, "daily", daily)));
        }
        @GetMapping("/api/admin/health") ApiResponse<?> getHealth() {
            return ApiResponse.onSuccess(Map.of("serverUp", true, "databaseUp", true, "responseTimeMs", 28));
        }
        @GetMapping("/api/admin/test-login/status") ApiResponse<?> getTestLogin() { return ApiResponse.onSuccess(Map.of("enabled", false)); }
        @PostMapping("/api/admin/test-login/toggle") ApiResponse<?> updateTestLogin() { return ApiResponse.onSuccess(Map.of("enabled", true)); }
    }
}

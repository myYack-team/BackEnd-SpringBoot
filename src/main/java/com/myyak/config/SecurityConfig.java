package com.myyak.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myyak.apiPayload.ApiResponse;
import com.myyak.filter.JwtAuthenticationFilter;
import com.myyak.filter.AdminSessionFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AdminSessionFilter adminSessionFilter;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    // 인증이 필요 없는 공개 API 경로 (공통)
    private static final String[] COMMON_PUBLIC_URLS = {
            // 인증 관련
            "/api/auth/**",
            // 약 검색 (비로그인 사용자도 검색 가능)
            "/api/drugs/search/**",
            // 로그인 화면과 공개 정적 자산. 관리자 데이터는 별도 인가.
            "/admin/login.html", "/admin/css/**", "/admin/js/**", "/admin/images/**",
            // 정적 파일 (업로드된 이미지)
            "/uploads/**",
            // Swagger
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            // 헬스체크
            "/actuator/**"
    };

    // 개발 환경에서만 허용할 경로
    private static final String[] DEV_ONLY_URLS = {
            // H2 Console (개발용)
            "/h2-console/**",
            // 에러 로그 (개발 환경에서만 비인증 허용)
            "/api/error-log/**"
    };

    /**
     * 프로필에 따른 공개 URL 목록 반환
     */
    private String[] getPublicUrls() {
        List<String> urls = new ArrayList<>(Arrays.asList(COMMON_PUBLIC_URLS));

        // 개발 환경에서만 추가 경로 허용
        if ("dev".equalsIgnoreCase(activeProfile) || "local".equalsIgnoreCase(activeProfile)) {
            urls.addAll(Arrays.asList(DEV_ONLY_URLS));
        }

        return urls.toArray(new String[0]);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/admin/auth/request-code", "/api/admin/auth/verify").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/error-log", "/api/error-log/**").hasRole("ADMIN")
                        .requestMatchers("/admin/login.html", "/admin/css/**", "/admin/js/**", "/admin/images/**").permitAll()
                        .requestMatchers("/admin", "/admin/**", "/api/admin/**", "/api/drugs/batch", "/api/drugs/batch/**").hasRole("ADMIN")
                        .requestMatchers(getPublicUrls()).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            if (request.getServletPath().equals("/admin") || request.getServletPath().startsWith("/admin/")) {
                                response.sendRedirect("/admin/login.html?next=" + java.net.URLEncoder.encode(request.getRequestURI(), java.nio.charset.StandardCharsets.UTF_8));
                                return;
                            }
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(
                                    new ObjectMapper().writeValueAsString(
                                            ApiResponse.onFailure("AUTH401", "인증이 필요합니다.", null)
                                    )
                            );
                        })
                        .accessDeniedHandler((request, response, exceptionValue) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json;charset=UTF-8");
                            new ObjectMapper().writeValue(response.getWriter(), ApiResponse.onFailure("AUTH403", "관리자 권한이 필요합니다.", null));
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(adminSessionFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<AdminSessionFilter> adminFilterRegistration(AdminSessionFilter filter) {
        FilterRegistrationBean<AdminSessionFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}

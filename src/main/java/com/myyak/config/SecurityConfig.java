package com.myyak.config;

import com.myyak.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
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

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    // 인증이 필요 없는 공개 API 경로 (공통)
    private static final String[] COMMON_PUBLIC_URLS = {
            // 인증 관련
            "/api/auth/**",
            // 약 검색 (비로그인 사용자도 검색 가능)
            "/api/drugs/search/**",
            // 관리자 페이지 (정적 파일 + API)
            "/admin/**",
            "/api/admin/**",
            // 데이터 배치 (관리자 전용)
            "/api/drugs/batch/**",
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
                        .requestMatchers("/api/drugs/batch/**").permitAll()
                        .requestMatchers(getPublicUrls()).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        // 배치 API는 Security 필터 체인을 완전히 우회
        return (web) -> web.ignoring().requestMatchers("/api/drugs/batch/**");
    }
}

package com.myyak.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    // 프로덕션 환경에서 허용할 도메인
    private static final List<String> PRODUCTION_ORIGINS = List.of(
            "https://myyak.app",
            "https://www.myyak.app",
            "https://api.myyak.xyz"
    );

    // 개발 환경에서 허용할 도메인
    private static final List<String> DEVELOPMENT_ORIGINS = List.of(
            "http://localhost:3000",
            "http://localhost:8081",
            "http://localhost:19006",
            "exp://localhost:8081"
    );

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        boolean isProduction = "prod".equalsIgnoreCase(activeProfile) || "production".equalsIgnoreCase(activeProfile);
        log.info("CORS 설정 - 프로필: {}, 프로덕션 모드: {}", activeProfile, isProduction);

        List<String> origins = new ArrayList<>();

        if (isProduction) {
            // 프로덕션: 명시적 도메인만 허용
            origins.addAll(PRODUCTION_ORIGINS);

            // 환경변수로 추가된 Origin (프로덕션에서도 허용)
            if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
                for (String origin : allowedOrigins.split(",")) {
                    String trimmed = origin.trim();
                    if (!trimmed.isEmpty()) {
                        origins.add(trimmed);
                    }
                }
            }

            configuration.setAllowedOrigins(origins);
            // 프로덕션에서는 와일드카드 패턴 사용 안함
            configuration.setAllowedOriginPatterns(null);
        } else {
            // 개발 환경: 로컬 개발 도메인 허용
            origins.addAll(DEVELOPMENT_ORIGINS);

            // 환경변수로 추가된 Origin
            if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
                for (String origin : allowedOrigins.split(",")) {
                    String trimmed = origin.trim();
                    if (!trimmed.isEmpty()) {
                        origins.add(trimmed);
                    }
                }
            }

            configuration.setAllowedOrigins(origins);
            // 개발 환경에서 모든 Origin 패턴 허용
            configuration.setAllowedOriginPatterns(List.of("*"));
        }

        log.info("CORS 허용 Origins: {}", origins);

        // 허용할 HTTP 메서드
        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // 허용할 헤더
        configuration.setAllowedHeaders(List.of("*"));

        // 인증 정보 포함 허용
        configuration.setAllowCredentials(true);

        // 노출할 헤더
        configuration.setExposedHeaders(List.of(
                "Authorization",
                "Content-Type"
        ));

        // preflight 요청 캐시 시간 (1시간)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}

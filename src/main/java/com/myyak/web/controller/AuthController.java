package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.service.authService.AuthService;
import com.myyak.web.dto.AuthDTO.AuthRequestDTO;
import com.myyak.web.dto.AuthDTO.AuthResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Tag(name = "Auth", description = "인증 API")
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 카카오 로그인 페이지로 리다이렉트
     * 앱에서 WebBrowser로 이 엔드포인트를 열면 카카오 로그인 페이지로 이동
     *
     * STATELESS 세션 정책으로 인해 세션 대신 OAuth state 파라미터를 사용하여
     * app_redirect_uri를 전달합니다.
     */
    @Operation(summary = "카카오 로그인 시작", description = "카카오 OAuth 로그인 페이지로 리다이렉트합니다.")
    @GetMapping("/kakao/login")
    public void kakaoLoginRedirect(
            @Parameter(description = "앱 리다이렉트 URI (Expo Go: exp://..., Production: myyak://...)")
            @RequestParam(required = false) String app_redirect_uri,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        String baseUrl = getBaseUrl(request);

        // app_redirect_uri를 Base64로 인코딩하여 state 파라미터로 전달
        // STATELESS 세션 정책에서는 세션 사용이 불가하므로 state를 활용
        String state = null;
        if (app_redirect_uri != null && !app_redirect_uri.isBlank()) {
            state = Base64.getUrlEncoder().encodeToString(app_redirect_uri.getBytes(StandardCharsets.UTF_8));
            log.info("앱 리다이렉트 URI를 state로 인코딩: {} -> {}", app_redirect_uri, state);
        }

        String authUrl = authService.getKakaoAuthorizationUrl(baseUrl, state);
        log.info("카카오 로그인 페이지로 리다이렉트: {}, baseUrl: {}", authUrl, baseUrl);
        response.sendRedirect(authUrl);
    }

    /**
     * 요청에서 base URL 추출 (예: https://api.myyak.xyz)
     * Nginx 리버스 프록시 환경에서 X-Forwarded-Proto 헤더를 우선 확인
     */
    private String getBaseUrl(HttpServletRequest request) {
        // X-Forwarded-Proto 헤더 확인 (Nginx 프록시 환경)
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String scheme = (forwardedProto != null && !forwardedProto.isBlank())
                ? forwardedProto
                : request.getScheme();

        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        // 기본 포트는 생략
        if ((scheme.equals("http") && serverPort == 80) ||
            (scheme.equals("https") && serverPort == 443) ||
            forwardedProto != null) {  // 프록시 환경에서는 포트 생략
            return scheme + "://" + serverName;
        }
        return scheme + "://" + serverName + ":" + serverPort;
    }

    /**
     * 카카오 OAuth 콜백 처리
     * 카카오에서 인증 후 인가 코드를 전달받아 토큰 발급 후 앱으로 리다이렉트
     *
     * state 파라미터에서 app_redirect_uri를 디코딩하여 사용합니다.
     */
    @Operation(summary = "카카오 OAuth 콜백", description = "카카오 인증 후 콜백을 처리하고 앱으로 리다이렉트합니다.")
    @GetMapping("/kakao/callback")
    public void kakaoCallback(
            @Parameter(description = "인가 코드") @RequestParam(required = false) String code,
            @Parameter(description = "에러 코드") @RequestParam(required = false) String error,
            @Parameter(description = "에러 설명") @RequestParam(required = false) String error_description,
            @Parameter(description = "OAuth state (Base64 인코딩된 app_redirect_uri)") @RequestParam(required = false) String state,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        // state 파라미터에서 앱 리다이렉트 URI 디코딩 (STATELESS 세션 정책 대응)
        String appRedirectUri = decodeAppRedirectUri(state);
        log.info("앱 리다이렉트 URI (state에서 디코딩): {}", appRedirectUri);

        // 에러 발생 시 앱으로 에러 전달
        if (error != null) {
            log.error("카카오 OAuth 에러: {} - {}", error, error_description);
            String errorMessage = error_description != null ? error_description : error;
            String redirectUrl = appRedirectUri + "?error=" + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
            response.sendRedirect(redirectUrl);
            return;
        }

        // 인가 코드 없으면 에러
        if (code == null || code.isBlank()) {
            log.error("카카오 OAuth 인가 코드 없음");
            String redirectUrl = appRedirectUri + "?error=" + URLEncoder.encode("인가 코드가 없습니다", StandardCharsets.UTF_8);
            response.sendRedirect(redirectUrl);
            return;
        }

        try {
            // 인가 코드로 로그인 처리 (동적 redirect_uri 사용)
            String baseUrl = getBaseUrl(request);
            log.debug("콜백 처리 baseUrl: {}", baseUrl);
            AuthResponseDTO.LoginResponse loginResponse = authService.loginWithKakaoCode(code, baseUrl);

            // 앱으로 리다이렉트 (Expo Go: exp://..., Production: myyak://...)
            String redirectUrl = String.format(
                    "%s?accessToken=%s&refreshToken=%s&isNewUser=%s",
                    appRedirectUri,
                    loginResponse.getAccessToken(),
                    loginResponse.getRefreshToken(),
                    loginResponse.isNewUser()
            );

            log.info("카카오 로그인 성공, 앱으로 리다이렉트: isNewUser={}, redirectUrl={}", loginResponse.isNewUser(), redirectUrl);
            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            log.error("카카오 로그인 처리 실패", e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "로그인 처리 실패";
            String redirectUrl = appRedirectUri + "?error=" + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
            response.sendRedirect(redirectUrl);
        }
    }

    @Operation(summary = "카카오 토큰 로그인", description = "카카오 액세스 토큰으로 직접 로그인합니다. (하위 호환용)")
    @PostMapping("/kakao")
    public ApiResponse<AuthResponseDTO.LoginResponse> loginWithKakao(
            @Valid @RequestBody AuthRequestDTO.KakaoLoginRequest request) {
        log.info("카카오 토큰 로그인 요청");
        return ApiResponse.onSuccess(authService.loginWithKakao(request));
    }

    @Operation(summary = "토큰 갱신", description = "리프레시 토큰으로 새로운 액세스 토큰을 발급받습니다.")
    @PostMapping("/refresh")
    public ApiResponse<AuthResponseDTO.TokenResponse> refreshToken(
            @Valid @RequestBody AuthRequestDTO.RefreshRequest request) {
        log.info("토큰 갱신 요청");
        return ApiResponse.onSuccess(authService.refreshToken(request));
    }

    @Operation(summary = "로그아웃", description = "현재 사용자의 리프레시 토큰을 삭제합니다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        log.info("로그아웃 요청: userId={}", userId);
        authService.logout(userId);
        return ApiResponse.onSuccess(null);
    }

    /**
     * OAuth state 파라미터에서 app_redirect_uri를 디코딩
     * state가 없거나 디코딩 실패 시 기본값 반환
     */
    private String decodeAppRedirectUri(String state) {
        if (state == null || state.isBlank()) {
            log.debug("state 파라미터가 없음, 기본 redirect URI 사용");
            return "myyak://oauth/callback";
        }

        try {
            String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
            log.debug("state 디코딩 성공: {}", decoded);
            return decoded;
        } catch (Exception e) {
            log.warn("state 디코딩 실패, 기본 redirect URI 사용: {}", e.getMessage());
            return "myyak://oauth/callback";
        }
    }
}

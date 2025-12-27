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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
     */
    @Operation(summary = "카카오 로그인 시작", description = "카카오 OAuth 로그인 페이지로 리다이렉트합니다.")
    @GetMapping("/kakao/login")
    public void kakaoLoginRedirect(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String baseUrl = getBaseUrl(request);
        String authUrl = authService.getKakaoAuthorizationUrl(baseUrl);
        log.info("카카오 로그인 페이지로 리다이렉트: {}, baseUrl: {}", authUrl, baseUrl);
        response.sendRedirect(authUrl);
    }

    /**
     * 요청에서 base URL 추출 (예: http://192.168.45.32:8080)
     */
    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        // 기본 포트는 생략
        if ((scheme.equals("http") && serverPort == 80) ||
            (scheme.equals("https") && serverPort == 443)) {
            return scheme + "://" + serverName;
        }
        return scheme + "://" + serverName + ":" + serverPort;
    }

    /**
     * 카카오 OAuth 콜백 처리
     * 카카오에서 인증 후 인가 코드를 전달받아 토큰 발급 후 앱으로 리다이렉트
     */
    @Operation(summary = "카카오 OAuth 콜백", description = "카카오 인증 후 콜백을 처리하고 앱으로 리다이렉트합니다.")
    @GetMapping("/kakao/callback")
    public void kakaoCallback(
            @Parameter(description = "인가 코드") @RequestParam(required = false) String code,
            @Parameter(description = "에러 코드") @RequestParam(required = false) String error,
            @Parameter(description = "에러 설명") @RequestParam(required = false) String error_description,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        // 에러 발생 시 앱으로 에러 전달
        if (error != null) {
            log.error("카카오 OAuth 에러: {} - {}", error, error_description);
            String errorMessage = error_description != null ? error_description : error;
            String deepLink = "myyak://oauth/callback?error=" + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
            response.sendRedirect(deepLink);
            return;
        }

        // 인가 코드 없으면 에러
        if (code == null || code.isBlank()) {
            log.error("카카오 OAuth 인가 코드 없음");
            String deepLink = "myyak://oauth/callback?error=" + URLEncoder.encode("인가 코드가 없습니다", StandardCharsets.UTF_8);
            response.sendRedirect(deepLink);
            return;
        }

        try {
            // 인가 코드로 로그인 처리 (동적 redirect_uri 사용)
            String baseUrl = getBaseUrl(request);
            log.debug("콜백 처리 baseUrl: {}", baseUrl);
            AuthResponseDTO.LoginResponse loginResponse = authService.loginWithKakaoCode(code, baseUrl);

            // 딥링크로 앱에 토큰 전달
            String deepLink = String.format(
                    "myyak://oauth/callback?accessToken=%s&refreshToken=%s&isNewUser=%s",
                    loginResponse.getAccessToken(),
                    loginResponse.getRefreshToken(),
                    loginResponse.isNewUser()
            );

            log.info("카카오 로그인 성공, 앱으로 리다이렉트: isNewUser={}", loginResponse.isNewUser());
            response.sendRedirect(deepLink);

        } catch (Exception e) {
            log.error("카카오 로그인 처리 실패", e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "로그인 처리 실패";
            String deepLink = "myyak://oauth/callback?error=" + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
            response.sendRedirect(deepLink);
        }
    }

    /**
     * 카카오 액세스 토큰으로 직접 로그인 (기존 방식 유지, 필요 시 사용)
     */
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
}

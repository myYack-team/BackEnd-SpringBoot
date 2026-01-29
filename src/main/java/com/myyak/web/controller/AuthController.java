package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.service.authService.AuthCodeStore;
import com.myyak.service.authService.AuthService;
import com.myyak.service.authService.OAuthStateStore;
import com.myyak.service.authService.RedirectUriValidator;
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
    private final OAuthStateStore oAuthStateStore;
    private final AuthCodeStore authCodeStore;
    private final RedirectUriValidator redirectUriValidator;

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

        // 1. Validate app_redirect_uri against allowlist (use default if invalid)
        String validatedUri = app_redirect_uri;
        if (validatedUri == null || validatedUri.isBlank() || !redirectUriValidator.isAllowed(validatedUri)) {
            validatedUri = redirectUriValidator.getDefaultRedirectUri();
            log.warn("Invalid or missing app_redirect_uri, using default: {}", validatedUri);
        }

        // 2. Create state with validated URI
        String state = oAuthStateStore.createState(validatedUri);
        log.info("Created OAuth state for redirect URI: {}", validatedUri);

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
     * state 파라미터를 검증하여 CSRF 공격을 방지하고, 앱 리다이렉트 URI를 복원합니다.
     */
    @Operation(summary = "카카오 OAuth 콜백", description = "카카오 인증 후 콜백을 처리하고 앱으로 리다이렉트합니다.")
    @GetMapping("/kakao/callback")
    public void kakaoCallback(
            @Parameter(description = "인가 코드") @RequestParam(required = false) String code,
            @Parameter(description = "에러 코드") @RequestParam(required = false) String error,
            @Parameter(description = "에러 설명") @RequestParam(required = false) String error_description,
            @Parameter(description = "OAuth state (CSRF 방지용)") @RequestParam(required = false) String state,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        // 1. Validate and consume state (CSRF protection)
        String appRedirectUri = oAuthStateStore.validateAndConsume(state);
        if (appRedirectUri == null) {
            log.error("Invalid or expired OAuth state: {}", state);
            String fallbackUri = redirectUriValidator.getDefaultRedirectUri();
            String redirectUrl = fallbackUri + "?error=" + URLEncoder.encode("잘못된 요청입니다", StandardCharsets.UTF_8);
            response.sendRedirect(redirectUrl);
            return;
        }
        log.info("OAuth state validated, redirect URI: {}", appRedirectUri);

        // 2. Handle OAuth errors from Kakao
        if (error != null) {
            log.error("카카오 OAuth 에러: {} - {}", error, error_description);
            String errorMessage = error_description != null ? error_description : error;
            String redirectUrl = appRedirectUri + "?error=" + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
            response.sendRedirect(redirectUrl);
            return;
        }

        // 3. Validate authorization code
        if (code == null || code.isBlank()) {
            log.error("카카오 OAuth 인가 코드 없음");
            String redirectUrl = appRedirectUri + "?error=" + URLEncoder.encode("인가 코드가 없습니다", StandardCharsets.UTF_8);
            response.sendRedirect(redirectUrl);
            return;
        }

        try {
            // 4. Process Kakao login
            String baseUrl = getBaseUrl(request);
            log.debug("콜백 처리 baseUrl: {}", baseUrl);
            AuthResponseDTO.LoginResponse loginResponse = authService.loginWithKakaoCode(code, baseUrl);

            // 5. Create auth code for token exchange (instead of exposing tokens in URL)
            String authCode = authCodeStore.createCode(
                    loginResponse.getAccessToken(),
                    loginResponse.getRefreshToken(),
                    loginResponse.isNewUser(),
                    loginResponse.getTermsAgreed(),
                    loginResponse.getPrivacyAgreed()
            );

            // 6. Redirect with auth code only (NO tokens in URL)
            String redirectUrl = appRedirectUri + "?code=" + authCode;
            log.info("카카오 로그인 성공, 앱으로 리다이렉트: isNewUser={}, code={}", loginResponse.isNewUser(), authCode);
            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            log.error("카카오 로그인 처리 실패", e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "로그인 처리 실패";
            String redirectUrl = appRedirectUri + "?error=" + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
            response.sendRedirect(redirectUrl);
        }
    }

    @Operation(summary = "인증 코드 교환", description = "OAuth 콜백에서 받은 인증 코드를 토큰으로 교환합니다.")
    @PostMapping("/exchange")
    public ApiResponse<AuthResponseDTO.ExchangeResponse> exchangeCode(
            @Valid @RequestBody AuthRequestDTO.ExchangeRequest request) {
        log.info("인증 코드 교환 요청");

        // Exchange auth code for tokens
        AuthCodeStore.CodeEntry codeEntry = authCodeStore.exchangeCode(request.getCode());
        if (codeEntry == null) {
            throw new GeneralException(ErrorStatus.AUTH_INVALID_CODE);
        }

        // Build response
        AuthResponseDTO.ExchangeResponse response = AuthResponseDTO.ExchangeResponse.builder()
                .accessToken(codeEntry.accessToken())
                .refreshToken(codeEntry.refreshToken())
                .isNewUser(codeEntry.isNewUser())
                .termsAgreed(codeEntry.termsAgreed())
                .privacyAgreed(codeEntry.privacyAgreed())
                .build();

        log.info("인증 코드 교환 성공: isNewUser={}", codeEntry.isNewUser());
        return ApiResponse.onSuccess(response);
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

    @Operation(summary = "테스트 로그인", description = "Google Play Store 심사용 테스트 계정으로 로그인합니다.")
    @PostMapping("/test-login")
    public ApiResponse<AuthResponseDTO.LoginResponse> testLogin() {
        log.info("테스트 로그인 요청");
        return ApiResponse.onSuccess(authService.testLogin());
    }

}

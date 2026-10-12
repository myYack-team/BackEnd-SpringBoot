package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.config.AdminAccessProperties;
import com.myyak.filter.AdminSessionFilter;
import com.myyak.service.adminAuthService.AdminAuthService;
import com.myyak.web.dto.AdminDTO.AdminAuthRequestDTO;
import com.myyak.web.dto.AdminDTO.AdminAuthResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
@Tag(name = "AdminAuth", description = "관리자 이메일 인증과 세션")
@RequiredArgsConstructor
public class AdminAuthController {
    private final AdminAuthService service;
    private final AdminAccessProperties properties;

    @PostMapping("/request-code")
    @Operation(summary = "설정된 관리자 이메일로 로그인 코드 발송")
    public ApiResponse<AdminAuthResponseDTO.Challenge> createChallenge() {
        return ApiResponse.onSuccess(new AdminAuthResponseDTO.Challenge(service.createChallenge(), 300));
    }

    @PostMapping("/verify")
    @Operation(summary = "인증 코드를 일회 소비하여 관리자 세션 생성")
    public ApiResponse<Void> createSession(@Valid @RequestBody AdminAuthRequestDTO.VerifyCode request,
            HttpServletResponse response) {
        String token = service.createSession(request.getChallenge(), request.getCode());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, properties.getSessionSeconds()).toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ApiResponse.onSuccess(null);
    }

    @GetMapping("/session")
    @Operation(summary = "관리자 세션과 요청 검증 정보 조회")
    public ApiResponse<AdminAuthResponseDTO.Session> getSession(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ApiResponse.onSuccess(new AdminAuthResponseDTO.Session((String) request.getAttribute(AdminSessionFilter.CSRF_ATTRIBUTE)));
    }

    @PostMapping("/logout")
    @Operation(summary = "관리자 세션 폐기")
    public ApiResponse<Void> deleteSession(HttpServletRequest request, HttpServletResponse response) {
        service.deleteSession(AdminSessionFilter.getSessionToken(request));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0).toString());
        return ApiResponse.onSuccess(null);
    }

    private ResponseCookie cookie(String token, long maxAge) {
        return ResponseCookie.from(AdminSessionFilter.COOKIE, token).path("/").httpOnly(true)
                .secure(properties.isSecureCookie()).sameSite("Strict").maxAge(maxAge).build();
    }
}

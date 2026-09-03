package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.service.fcmService.FcmService;
import com.myyak.web.dto.FcmDTO.FcmRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "FCM", description = "푸시 알림 API")
@RestController
@RequestMapping("/api/fcm")
@RequiredArgsConstructor
public class FcmController {

    private final FcmService fcmService;

    @Operation(summary = "FCM 토큰 등록", description = "클라이언트의 FCM 토큰을 서버에 등록합니다.")
    @PostMapping("/token")
    public ApiResponse<String> registerToken(
            Authentication authentication,
            @Valid @RequestBody FcmRequestDTO.RegisterToken request) {
        Long userId = (Long) authentication.getPrincipal();
        fcmService.registerToken(userId, request.getFcmToken());
        return ApiResponse.onSuccess("FCM 토큰이 등록되었습니다.");
    }

    @Operation(summary = "FCM 토큰 해제", description = "로그아웃 등으로 현재 사용자의 FCM 토큰을 서버에서 해제합니다.")
    @DeleteMapping("/token")
    public ApiResponse<String> unregisterToken(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        fcmService.unregisterToken(userId);
        return ApiResponse.onSuccess("FCM 토큰이 해제되었습니다.");
    }
}

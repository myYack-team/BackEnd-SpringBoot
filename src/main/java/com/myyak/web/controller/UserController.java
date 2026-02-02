package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.service.userService.UserService;
import com.myyak.web.dto.UserDTO.UserRequestDTO;
import com.myyak.web.dto.UserDTO.UserResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "사용자 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 정보를 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<UserResponseDTO.UserInfo> getMyInfo(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(userService.getMyInfo(userId));
    }

    @Operation(summary = "내 정보 수정", description = "현재 로그인한 사용자의 정보를 수정합니다.")
    @PatchMapping("/me")
    public ApiResponse<UserResponseDTO.UpdateResult> updateMyInfo(
            Authentication authentication,
            @RequestBody UserRequestDTO.UpdateRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(userService.updateMyInfo(userId, request));
    }

    @Operation(summary = "회원 탈퇴", description = "현재 로그인한 사용자의 계정과 모든 관련 데이터를 삭제합니다.")
    @DeleteMapping("/me")
    public ApiResponse<Void> deleteMe(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        userService.deleteMe(userId);
        return ApiResponse.onSuccess(null);
    }

    @Operation(summary = "알림 설정 조회", description = "현재 로그인한 사용자의 알림 설정을 조회합니다.")
    @GetMapping("/me/notification-settings")
    public ApiResponse<UserResponseDTO.NotificationSettings> getNotificationSettings(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(userService.getNotificationSettings(userId));
    }

    @Operation(summary = "알림 설정 수정", description = "현재 로그인한 사용자의 알림 설정을 수정합니다.")
    @PatchMapping("/me/notification-settings")
    public ApiResponse<UserResponseDTO.NotificationSettings> updateNotificationSettings(
            Authentication authentication,
            @Valid @RequestBody UserRequestDTO.UpdateNotificationSettings request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(userService.updateNotificationSettings(userId, request));
    }

    @Operation(summary = "기본정보 설정", description = "신규 가입자의 기본정보(성별, 연령대, 가입목적)를 설정합니다.")
    @PutMapping("/me/profile-setup")
    public ApiResponse<UserResponseDTO.ProfileSetupResult> setupProfile(
            Authentication authentication,
            @Valid @RequestBody UserRequestDTO.ProfileSetupRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(userService.setupProfile(userId, request));
    }

    @Operation(summary = "AI 데이터 동의 상태 조회", description = "현재 로그인한 사용자의 AI 데이터 동의 상태를 조회합니다.")
    @GetMapping("/me/ai-consent")
    public ApiResponse<UserResponseDTO.AiConsentStatus> getAiConsentStatus(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(userService.getAiConsentStatus(userId));
    }

    @Operation(summary = "AI 데이터 동의 상태 변경", description = "현재 로그인한 사용자의 AI 데이터 동의 상태를 변경합니다.")
    @PatchMapping("/me/ai-consent")
    public ApiResponse<UserResponseDTO.AiConsentStatus> updateAiConsent(
            Authentication authentication,
            @Valid @RequestBody UserRequestDTO.UpdateAiConsentRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(userService.updateAiConsent(userId, request));
    }

    @Operation(summary = "서비스 이용 동의 상태 조회", description = "현재 로그인한 사용자의 이용약관 및 개인정보 처리방침 동의 상태를 조회합니다.")
    @GetMapping("/me/consent")
    public ApiResponse<UserResponseDTO.ConsentStatus> getConsentStatus(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(userService.getConsentStatus(userId));
    }

    @Operation(summary = "서비스 이용 동의 제출", description = "이용약관 및 개인정보 처리방침에 동의합니다.")
    @PostMapping("/me/consent")
    public ApiResponse<UserResponseDTO.ConsentStatus> submitConsent(
            Authentication authentication,
            @Valid @RequestBody UserRequestDTO.ConsentRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(userService.submitConsent(userId, request));
    }

    @Operation(summary = "전화번호 등록/수정", description = "현재 로그인한 사용자의 전화번호를 등록하거나 수정합니다. 가족 연동 기능에 사용됩니다.")
    @PatchMapping("/me/phone")
    public ApiResponse<UserResponseDTO.PhoneUpdateResult> updatePhone(
            Authentication authentication,
            @Valid @RequestBody UserRequestDTO.UpdatePhoneRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(userService.updatePhone(userId, request));
    }
}

package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.service.familyService.FamilyService;
import com.myyak.web.dto.FamilyDTO.FamilyRequestDTO;
import com.myyak.web.dto.FamilyDTO.FamilyResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Family", description = "가족 연동 API")
@RestController
@RequestMapping("/api/family")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;

    @Operation(summary = "가족 연동 현황 조회", description = "현재 사용자의 가족 연동 현황을 조회합니다. 연결된 가족, 보낸/받은 요청, 나를 보호자로 등록한 사람들을 포함합니다.")
    @GetMapping("/status")
    public ApiResponse<FamilyResponseDTO.LinkStatus> getLinkStatus(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(familyService.getLinkStatus(userId));
    }

    @Operation(summary = "가족 연동 요청 전송", description = "전화번호로 가족 연동 요청을 전송합니다. 요청을 보내려면 본인의 전화번호가 등록되어 있어야 합니다.")
    @PostMapping("/request")
    public ApiResponse<FamilyResponseDTO.SendRequestResult> sendLinkRequest(
            Authentication authentication,
            @Valid @RequestBody FamilyRequestDTO.SendRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(familyService.sendLinkRequest(userId, request));
    }

    @Operation(summary = "가족 연동 요청 취소", description = "내가 보낸 가족 연동 요청을 취소합니다.")
    @DeleteMapping("/request/{requestId}")
    public ApiResponse<Void> cancelLinkRequest(
            Authentication authentication,
            @Parameter(description = "요청 ID") @PathVariable Long requestId) {
        Long userId = (Long) authentication.getPrincipal();
        familyService.cancelLinkRequest(userId, requestId);
        return ApiResponse.onSuccess(null);
    }

    @Operation(summary = "가족 연동 요청 수락", description = "받은 가족 연동 요청을 수락합니다.")
    @PostMapping("/request/{requestId}/accept")
    public ApiResponse<Void> acceptLinkRequest(
            Authentication authentication,
            @Parameter(description = "요청 ID") @PathVariable Long requestId) {
        Long userId = (Long) authentication.getPrincipal();
        familyService.acceptLinkRequest(userId, requestId);
        return ApiResponse.onSuccess(null);
    }

    @Operation(summary = "가족 연동 요청 거절", description = "받은 가족 연동 요청을 거절합니다.")
    @PostMapping("/request/{requestId}/reject")
    public ApiResponse<Void> rejectLinkRequest(
            Authentication authentication,
            @Parameter(description = "요청 ID") @PathVariable Long requestId) {
        Long userId = (Long) authentication.getPrincipal();
        familyService.rejectLinkRequest(userId, requestId);
        return ApiResponse.onSuccess(null);
    }

    @Operation(summary = "가족 연동 해제", description = "기존 가족 연동을 해제합니다. 보호자 또는 피보호자 모두 해제할 수 있습니다.")
    @DeleteMapping("/link/{linkId}")
    public ApiResponse<Void> unlinkFamily(
            Authentication authentication,
            @Parameter(description = "연동 ID") @PathVariable Long linkId) {
        Long userId = (Long) authentication.getPrincipal();
        familyService.unlinkFamily(userId, linkId);
        return ApiResponse.onSuccess(null);
    }

    @Operation(summary = "피보호자 오늘 복약 일정 조회", description = "보호자로서 피보호자의 오늘 복약 일정을 조회합니다.")
    @GetMapping("/protected/{userId}/today")
    public ApiResponse<FamilyResponseDTO.FamilyTodaySchedule> getFamilyTodaySchedule(
            Authentication authentication,
            @Parameter(description = "피보호자 ID") @PathVariable Long userId) {
        Long guardianId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(familyService.getFamilyTodaySchedule(guardianId, userId));
    }

    @Operation(summary = "피보호자 특정 날짜 복약 일정 조회", description = "보호자로서 피보호자의 특정 날짜 복약 일정을 조회합니다.")
    @GetMapping("/protected/{userId}/schedule")
    public ApiResponse<FamilyResponseDTO.FamilyTodaySchedule> getFamilyScheduleForDate(
            Authentication authentication,
            @Parameter(description = "피보호자 ID") @PathVariable Long userId,
            @Parameter(description = "조회할 날짜 (yyyy-MM-dd)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long guardianId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(familyService.getFamilyScheduleForDate(guardianId, userId, date));
    }

    @Operation(summary = "피보호자 월간 복약 요약 조회", description = "보호자로서 피보호자의 월간 복약 요약을 조회합니다.")
    @GetMapping("/protected/{userId}/monthly-summary")
    public ApiResponse<FamilyResponseDTO.FamilyMonthlySummary> getFamilyMonthlySummary(
            Authentication authentication,
            @Parameter(description = "피보호자 ID") @PathVariable Long userId,
            @Parameter(description = "년도") @RequestParam Integer year,
            @Parameter(description = "월") @RequestParam Integer month) {
        Long guardianId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(familyService.getFamilyMonthlySummary(guardianId, userId, year, month));
    }

    @Operation(summary = "가족 알림 설정 조회", description = "현재 사용자의 가족 알림 설정을 조회합니다.")
    @GetMapping("/notification-settings")
    public ApiResponse<FamilyResponseDTO.FamilyNotificationSettings> getFamilyNotificationSettings(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(familyService.getFamilyNotificationSettings(userId));
    }

    @Operation(summary = "가족 알림 설정 수정", description = "가족 알림 수신 여부를 설정합니다. 비활성화하면 보호자에게 미복용 알림이 전송되지 않습니다.")
    @PatchMapping("/notification-settings")
    public ApiResponse<FamilyResponseDTO.FamilyNotificationSettings> updateFamilyNotificationSettings(
            Authentication authentication,
            @Valid @RequestBody FamilyRequestDTO.UpdateFamilyNotificationSettings request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(familyService.updateFamilyNotificationSettings(userId, request));
    }
}

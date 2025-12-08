package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.apiPayload.code.status.SuccessStatus;
import com.myyak.service.reminderService.ReminderService;
import com.myyak.web.dto.ReminderDTO.ReminderRequestDTO;
import com.myyak.web.dto.ReminderDTO.ReminderResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Reminder", description = "알림 API")
@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @Operation(summary = "알림 목록 조회", description = "사용자의 알림 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<ReminderResponseDTO.ReminderList> getReminders(
            @Parameter(description = "사용자 ID (임시)")
            @RequestParam Long userId) {
        return ApiResponse.onSuccess(reminderService.getReminders(userId));
    }

    @Operation(summary = "알림 시간 수정", description = "알림 시간을 수정합니다.")
    @PatchMapping("/{reminderId}")
    public ApiResponse<ReminderResponseDTO.UpdateResult> updateReminderTime(
            @Parameter(description = "사용자 ID (임시)")
            @RequestParam Long userId,
            @Parameter(description = "알림 ID")
            @PathVariable Long reminderId,
            @Valid @RequestBody ReminderRequestDTO.UpdateTimeRequest request) {
        return ApiResponse.of(SuccessStatus.REMINDER_UPDATED, reminderService.updateReminderTime(userId, reminderId, request));
    }

    @Operation(summary = "알림 ON/OFF", description = "알림을 켜거나 끕니다.")
    @PatchMapping("/{reminderId}/toggle")
    public ApiResponse<ReminderResponseDTO.ToggleResult> toggleReminder(
            @Parameter(description = "사용자 ID (임시)")
            @RequestParam Long userId,
            @Parameter(description = "알림 ID")
            @PathVariable Long reminderId) {
        return ApiResponse.of(SuccessStatus.REMINDER_UPDATED, reminderService.toggleReminder(userId, reminderId));
    }
}

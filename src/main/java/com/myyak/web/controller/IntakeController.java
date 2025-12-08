package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.apiPayload.code.status.SuccessStatus;
import com.myyak.service.intakeService.IntakeService;
import com.myyak.web.dto.IntakeDTO.IntakeRequestDTO;
import com.myyak.web.dto.IntakeDTO.IntakeResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Intake", description = "복약 기록 API")
@RestController
@RequestMapping("/api/intakes")
@RequiredArgsConstructor
public class IntakeController {

    private final IntakeService intakeService;

    @Operation(summary = "복약 기록", description = "복약 완료를 기록합니다.")
    @PostMapping
    public ApiResponse<IntakeResponseDTO.CreateResult> recordIntake(
            @Parameter(description = "사용자 ID (임시)")
            @RequestParam Long userId,
            @Valid @RequestBody IntakeRequestDTO.CreateRequest request) {
        return ApiResponse.of(SuccessStatus.INTAKE_RECORDED, intakeService.recordIntake(userId, request));
    }

    @Operation(summary = "복약 기록 조회", description = "특정 날짜의 복약 기록을 조회합니다.")
    @GetMapping
    public ApiResponse<IntakeResponseDTO.DailyIntakeResult> getIntakes(
            @Parameter(description = "사용자 ID (임시)")
            @RequestParam Long userId,
            @Parameter(description = "조회 날짜 (YYYY-MM-DD), 미입력시 오늘")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        return ApiResponse.onSuccess(intakeService.getIntakes(userId, date));
    }
}

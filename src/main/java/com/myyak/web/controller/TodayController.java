package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.service.todayService.TodayService;
import com.myyak.web.dto.TodayDTO.TodayResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Today", description = "오늘의 복약 API")
@RestController
@RequestMapping("/api/today")
@RequiredArgsConstructor
public class TodayController {

    private final TodayService todayService;

    @Operation(summary = "오늘의 복약 현황", description = "오늘 복용해야 할 약 목록과 복용 여부를 조회합니다.")
    @GetMapping
    public ApiResponse<TodayResponseDTO.TodayResult> getTodaySchedule(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(todayService.getTodaySchedule(userId));
    }
}

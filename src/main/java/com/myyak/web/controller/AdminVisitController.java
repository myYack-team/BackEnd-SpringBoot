package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.service.visitService.VisitService;
import com.myyak.web.dto.VisitDTO.VisitResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/visits")
@Tag(name = "AdminVisit", description = "일일 앱 방문자 통계")
public class AdminVisitController {
    private final VisitService service;

    @GetMapping
    @Operation(summary = "오늘·어제·최근 7일 방문자 조회", description = "한국 시간 기준 날짜별 고유 로그인 사용자 수를 반환합니다.")
    public ApiResponse<VisitResponseDTO.Summary> getVisitSummary(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.onSuccess(service.getVisitSummary());
    }
}

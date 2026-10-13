package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.service.visitService.VisitService;
import com.myyak.web.dto.VisitDTO.VisitResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/visits")
@Tag(name = "UserVisit", description = "로그인 사용자의 앱 방문 기록")
public class UserVisitController {
    private final VisitService service;

    @PostMapping
    @Operation(summary = "오늘 앱 방문 기록", description = "인증된 계정을 한국 시간 기준 하루 한 번 기록합니다. 날짜와 사용자 ID는 서버가 결정합니다.")
    public ApiResponse<VisitResponseDTO.RecordResult> createVisit(Authentication authentication) {
        return ApiResponse.onSuccess(service.createVisit((Long) authentication.getPrincipal()));
    }
}

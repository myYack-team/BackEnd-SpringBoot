package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.apiPayload.code.status.SuccessStatus;
import com.myyak.service.analysisService.AnalysisService;
import com.myyak.web.dto.AnalysisDTO.AnalysisResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * AI 약물 분석 API 컨트롤러
 */
@Tag(name = "Analysis", description = "AI 약물 분석 API")
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @Operation(summary = "AI 분석 요청",
            description = "사용자의 복용 약물을 AI로 분석합니다. 약물의 작용 메커니즘과 음식 상호작용 정보를 제공합니다.")
    @PostMapping("/request")
    public ApiResponse<AnalysisResponseDTO.AnalysisResult> requestAnalysis(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.of(SuccessStatus.ANALYSIS_COMPLETED, analysisService.requestAnalysis(userId));
    }

    @Operation(summary = "분석 레포트 목록 조회",
            description = "사용자의 AI 분석 레포트 목록을 조회합니다.")
    @GetMapping("/reports")
    public ApiResponse<AnalysisResponseDTO.ReportList> getReportList(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(analysisService.getReportList(userId));
    }

    @Operation(summary = "분석 레포트 상세 조회",
            description = "특정 AI 분석 레포트의 상세 정보를 조회합니다.")
    @GetMapping("/reports/{reportId}")
    public ApiResponse<AnalysisResponseDTO.AnalysisResult> getReportDetail(
            Authentication authentication,
            @Parameter(description = "레포트 ID")
            @PathVariable Long reportId) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(analysisService.getReportDetail(userId, reportId));
    }

    @Operation(summary = "분석 레포트 삭제",
            description = "AI 분석 레포트를 삭제합니다.")
    @DeleteMapping("/reports/{reportId}")
    public ApiResponse<Void> deleteReport(
            Authentication authentication,
            @Parameter(description = "레포트 ID")
            @PathVariable Long reportId) {
        Long userId = (Long) authentication.getPrincipal();
        analysisService.deleteReport(userId, reportId);
        return ApiResponse.of(SuccessStatus.ANALYSIS_REPORT_DELETED, null);
    }

    // TODO: 유료 결제 도입 후 쿼터 조회 API 활성화
    // @Operation(summary = "분석 쿼터 조회",
    //         description = "이번 달 남은 AI 분석 횟수를 조회합니다.")
    // @GetMapping("/quota")
    // public ApiResponse<AnalysisResponseDTO.QuotaInfo> getQuotaInfo(Authentication authentication) {
    //     Long userId = (Long) authentication.getPrincipal();
    //     return ApiResponse.onSuccess(analysisService.getQuotaInfo(userId));
    // }
}

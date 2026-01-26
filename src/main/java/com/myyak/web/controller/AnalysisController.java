package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.apiPayload.code.status.SuccessStatus;
import com.myyak.service.analysisService.AnalysisService;
import com.myyak.web.dto.AnalysisDTO.AnalysisRequestDTO;
import com.myyak.web.dto.AnalysisDTO.AnalysisResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    @Operation(summary = "분석 쿼터 조회",
            description = "이번 달 남은 AI 분석 횟수를 조회합니다. 매월 1일에 리셋됩니다.")
    @GetMapping("/quota")
    public ApiResponse<AnalysisResponseDTO.QuotaInfo> getQuotaInfo(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(analysisService.getQuotaInfo(userId));
    }

    @Operation(summary = "AI 분석 데이터 충분성 확인",
            description = "AI 분석을 수행하기에 충분한 데이터가 있는지 확인합니다. 최근 30일간의 복약 기록과 건강 메모를 기준으로 판단합니다.")
    @GetMapping("/data-sufficiency")
    public ApiResponse<AnalysisResponseDTO.DataSufficiencyCheck> checkDataSufficiency(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(analysisService.checkDataSufficiency(userId));
    }

    @Operation(summary = "임시 건강 메모 저장",
            description = "AI 분석 요청 전 추가 컨디션/증상 정보를 임시로 저장합니다.")
    @PostMapping("/temporary-notes")
    public ApiResponse<Void> saveTemporaryNote(
            Authentication authentication,
            @Valid @RequestBody AnalysisRequestDTO.TemporaryNoteRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        analysisService.saveTemporaryNote(userId, request);
        return ApiResponse.onSuccess(null);
    }

    @Operation(summary = "임시 건강 메모 일괄 삭제",
            description = "사용자의 모든 임시 건강 메모를 삭제합니다.")
    @DeleteMapping("/temporary-notes")
    public ApiResponse<Void> deleteAllTemporaryNotes(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        analysisService.deleteAllTemporaryNotes(userId);
        return ApiResponse.onSuccess(null);
    }
}

package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.domain.enums.QnAStatus;
import com.myyak.service.qnaService.QnAService;
import com.myyak.web.dto.QnADTO.QnARequestDTO;
import com.myyak.web.dto.QnADTO.QnAResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Admin Q&A", description = "관리자 Q&A 관리 API")
@RestController
@RequestMapping("/api/admin/qna")
@RequiredArgsConstructor
public class AdminQnAController {

    private final QnAService qnaService;

    @Operation(summary = "전체 문의 목록 조회", description = "전체 문의 목록을 조회합니다. 상태별 필터링이 가능합니다.")
    @GetMapping
    public ApiResponse<QnAResponseDTO.AdminQuestionListResponse> getAllQuestions(
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "상태 필터 (PENDING, ANSWERED, CLOSED)")
            @RequestParam(required = false) QnAStatus status) {
        return ApiResponse.onSuccess(qnaService.getAllQuestions(page, size, status));
    }

    @Operation(summary = "문의 상세 조회", description = "특정 문의의 상세 내용과 모든 답변을 조회합니다.")
    @GetMapping("/{questionId}")
    public ApiResponse<QnAResponseDTO.QuestionDetail> getQuestionDetail(
            @Parameter(description = "문의 ID")
            @PathVariable Long questionId) {
        return ApiResponse.onSuccess(qnaService.getQuestionDetailForAdmin(questionId));
    }

    @Operation(summary = "관리자 답변 추가", description = "문의에 관리자 답변을 추가합니다. 첫 답변 시 상태가 자동으로 ANSWERED로 변경됩니다.")
    @PostMapping("/{questionId}/replies")
    public ApiResponse<QnAResponseDTO.ReplyResult> addAdminReply(
            @Parameter(description = "문의 ID")
            @PathVariable Long questionId,
            @Valid @RequestBody QnARequestDTO.AdminReply request) {
        log.info("관리자 답변 추가 요청 - questionId: {}, adminName: {}", questionId, request.getAdminName());
        return ApiResponse.onSuccess(qnaService.addAdminReply(questionId, request));
    }

    @Operation(summary = "문의 상태 변경", description = "문의의 상태를 변경합니다.")
    @PatchMapping("/{questionId}/status")
    public ApiResponse<QnAResponseDTO.QuestionDetail> updateQuestionStatus(
            @Parameter(description = "문의 ID")
            @PathVariable Long questionId,
            @Valid @RequestBody QnARequestDTO.UpdateStatus request) {
        log.info("문의 상태 변경 요청 - questionId: {}, newStatus: {}", questionId, request.getStatus());
        return ApiResponse.onSuccess(qnaService.updateQuestionStatus(questionId, request.getStatus()));
    }

    @Operation(summary = "Q&A 통계 조회", description = "전체 문의 수, 상태별 문의 수 통계를 조회합니다.")
    @GetMapping("/stats")
    public ApiResponse<QnAResponseDTO.AdminStatsResponse> getStats() {
        return ApiResponse.onSuccess(qnaService.getStats());
    }

    @Operation(summary = "미답변 문의 수 조회", description = "PENDING 상태의 문의 수를 조회합니다.")
    @GetMapping("/pending-count")
    public ApiResponse<QnAResponseDTO.PendingCountResponse> getPendingCount() {
        return ApiResponse.onSuccess(qnaService.getPendingCount());
    }
}

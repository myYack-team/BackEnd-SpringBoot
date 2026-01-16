package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.service.qnaService.QnAService;
import com.myyak.web.dto.QnADTO.QnARequestDTO;
import com.myyak.web.dto.QnADTO.QnAResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Q&A", description = "문의하기 API")
@RestController
@RequestMapping("/api/qna")
@RequiredArgsConstructor
public class QnAController {

    private final QnAService qnaService;

    @Operation(summary = "내 문의 목록 조회", description = "현재 로그인한 사용자의 문의 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<QnAResponseDTO.QuestionListResponse> getMyQuestions(
            Authentication authentication,
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "10") int size) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(qnaService.getMyQuestions(userId, page, size));
    }

    @Operation(summary = "문의 상세 조회", description = "특정 문의의 상세 내용과 답변을 조회합니다.")
    @GetMapping("/{questionId}")
    public ApiResponse<QnAResponseDTO.QuestionDetail> getQuestionDetail(
            Authentication authentication,
            @Parameter(description = "문의 ID")
            @PathVariable Long questionId) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(qnaService.getQuestionDetail(userId, questionId));
    }

    @Operation(summary = "새 문의 등록", description = "새로운 문의를 등록합니다.")
    @PostMapping
    public ApiResponse<QnAResponseDTO.CreateResult> createQuestion(
            Authentication authentication,
            @Valid @RequestBody QnARequestDTO.CreateQuestion request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(qnaService.createQuestion(userId, request));
    }

    @Operation(summary = "답글 추가", description = "기존 문의에 사용자 답글을 추가합니다.")
    @PostMapping("/{questionId}/replies")
    public ApiResponse<QnAResponseDTO.ReplyResult> addUserReply(
            Authentication authentication,
            @Parameter(description = "문의 ID")
            @PathVariable Long questionId,
            @Valid @RequestBody QnARequestDTO.CreateReply request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(qnaService.addUserReply(userId, questionId, request));
    }

    @Operation(summary = "문의 삭제", description = "자신의 문의를 삭제합니다.")
    @DeleteMapping("/{questionId}")
    public ApiResponse<Void> deleteQuestion(
            Authentication authentication,
            @Parameter(description = "문의 ID")
            @PathVariable Long questionId) {
        Long userId = (Long) authentication.getPrincipal();
        qnaService.deleteQuestion(userId, questionId);
        return ApiResponse.onSuccess(null);
    }
}

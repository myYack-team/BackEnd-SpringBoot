package com.myyak.service.qnaService;

import com.myyak.domain.enums.QnAStatus;
import com.myyak.web.dto.QnADTO.QnARequestDTO;
import com.myyak.web.dto.QnADTO.QnAResponseDTO;

public interface QnAService {

    // ===== User API =====

    /**
     * 내 문의 목록 조회
     */
    QnAResponseDTO.QuestionListResponse getMyQuestions(Long userId, int page, int size);

    /**
     * 문의 상세 조회
     */
    QnAResponseDTO.QuestionDetail getQuestionDetail(Long userId, Long questionId);

    /**
     * 새 문의 등록
     */
    QnAResponseDTO.CreateResult createQuestion(Long userId, QnARequestDTO.CreateQuestion request);

    /**
     * 사용자 답글 추가
     */
    QnAResponseDTO.ReplyResult addUserReply(Long userId, Long questionId, QnARequestDTO.CreateReply request);

    /**
     * 문의 삭제
     */
    void deleteQuestion(Long userId, Long questionId);

    // ===== Admin API =====

    /**
     * 전체 문의 목록 조회 (관리자용)
     */
    QnAResponseDTO.AdminQuestionListResponse getAllQuestions(int page, int size, QnAStatus status);

    /**
     * 문의 상세 조회 (관리자용)
     */
    QnAResponseDTO.QuestionDetail getQuestionDetailForAdmin(Long questionId);

    /**
     * 관리자 답변 추가
     */
    QnAResponseDTO.ReplyResult addAdminReply(Long questionId, QnARequestDTO.AdminReply request);

    /**
     * 문의 상태 변경
     */
    QnAResponseDTO.QuestionDetail updateQuestionStatus(Long questionId, QnAStatus status);

    /**
     * Q&A 통계 조회
     */
    QnAResponseDTO.AdminStatsResponse getStats();

    /**
     * 미답변 문의 수 조회
     */
    QnAResponseDTO.PendingCountResponse getPendingCount();
}

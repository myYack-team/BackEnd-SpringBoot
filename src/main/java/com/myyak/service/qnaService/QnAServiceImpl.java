package com.myyak.service.qnaService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.converter.QnAConverter;
import com.myyak.domain.QnA;
import com.myyak.domain.QnAReply;
import com.myyak.domain.User;
import com.myyak.domain.enums.QnAStatus;
import com.myyak.repository.QnAReplyRepository;
import com.myyak.repository.QnARepository;
import com.myyak.repository.UserRepository;
import com.myyak.web.dto.QnADTO.QnARequestDTO;
import com.myyak.web.dto.QnADTO.QnAResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QnAServiceImpl implements QnAService {

    private final QnARepository qnaRepository;
    private final QnAReplyRepository qnaReplyRepository;
    private final UserRepository userRepository;

    // ===== User API =====

    @Override
    public QnAResponseDTO.QuestionListResponse getMyQuestions(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<QnA> qnaPage = qnaRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        List<QnAResponseDTO.QuestionSummary> questions = qnaPage.getContent().stream()
                .map(QnAConverter::toQuestionSummary)
                .toList();

        return QnAResponseDTO.QuestionListResponse.builder()
                .questions(questions)
                .totalCount(qnaPage.getTotalElements())
                .page(page)
                .totalPages(qnaPage.getTotalPages())
                .hasNext(qnaPage.hasNext())
                .build();
    }

    @Override
    public QnAResponseDTO.QuestionDetail getQuestionDetail(Long userId, Long questionId) {
        QnA qna = findQnAById(questionId);

        // 소유권 검증
        if (!qna.getUser().getId().equals(userId)) {
            throw new GeneralException(ErrorStatus.QNA_ACCESS_DENIED);
        }

        return QnAConverter.toQuestionDetail(qna);
    }

    @Override
    @Transactional
    public QnAResponseDTO.CreateResult createQuestion(Long userId, QnARequestDTO.CreateQuestion request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        QnA qna = QnAConverter.toEntity(request, user);
        qnaRepository.save(qna);

        log.info("새 문의 등록 - userId: {}, questionId: {}, title: {}", userId, qna.getId(), qna.getTitle());

        return QnAConverter.toCreateResult(qna);
    }

    @Override
    @Transactional
    public QnAResponseDTO.ReplyResult addUserReply(Long userId, Long questionId, QnARequestDTO.CreateReply request) {
        QnA qna = findQnAById(questionId);

        // 소유권 검증
        if (!qna.getUser().getId().equals(userId)) {
            throw new GeneralException(ErrorStatus.QNA_ACCESS_DENIED);
        }

        QnAReply reply = QnAConverter.toReplyEntity(request, qna, false, null);
        qnaReplyRepository.save(reply);
        qna.addReply(reply);

        log.info("사용자 답글 추가 - userId: {}, questionId: {}, replyId: {}", userId, questionId, reply.getId());

        return QnAConverter.toReplyResult(reply);
    }

    @Override
    @Transactional
    public void deleteQuestion(Long userId, Long questionId) {
        QnA qna = findQnAById(questionId);

        // 소유권 검증
        if (!qna.getUser().getId().equals(userId)) {
            throw new GeneralException(ErrorStatus.QNA_ACCESS_DENIED);
        }

        qnaRepository.delete(qna);

        log.info("문의 삭제 - userId: {}, questionId: {}", userId, questionId);
    }

    // ===== Admin API =====

    @Override
    public QnAResponseDTO.AdminQuestionListResponse getAllQuestions(int page, int size, QnAStatus status) {
        Pageable pageable = PageRequest.of(page, size);
        Page<QnA> qnaPage;

        if (status != null) {
            qnaPage = qnaRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            qnaPage = qnaRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        List<QnAResponseDTO.AdminQuestionSummary> questions = qnaPage.getContent().stream()
                .map(QnAConverter::toAdminQuestionSummary)
                .toList();

        return QnAResponseDTO.AdminQuestionListResponse.builder()
                .questions(questions)
                .totalCount(qnaPage.getTotalElements())
                .page(page)
                .totalPages(qnaPage.getTotalPages())
                .hasNext(qnaPage.hasNext())
                .build();
    }

    @Override
    public QnAResponseDTO.QuestionDetail getQuestionDetailForAdmin(Long questionId) {
        QnA qna = findQnAById(questionId);
        return QnAConverter.toQuestionDetail(qna);
    }

    @Override
    @Transactional
    public QnAResponseDTO.ReplyResult addAdminReply(Long questionId, QnARequestDTO.AdminReply request) {
        QnA qna = findQnAById(questionId);

        QnAReply reply = QnAConverter.toAdminReplyEntity(request, qna);
        qnaReplyRepository.save(reply);
        qna.addReply(reply);

        // 관리자 답변 시 자동으로 상태를 ANSWERED로 변경
        if (qna.getStatus() == QnAStatus.PENDING) {
            qna.updateStatus(QnAStatus.ANSWERED);
        }

        log.info("관리자 답변 추가 - questionId: {}, replyId: {}, adminName: {}",
                questionId, reply.getId(), request.getAdminName());

        return QnAConverter.toReplyResult(reply);
    }

    @Override
    @Transactional
    public QnAResponseDTO.QuestionDetail updateQuestionStatus(Long questionId, QnAStatus status) {
        QnA qna = findQnAById(questionId);
        qna.updateStatus(status);

        log.info("문의 상태 변경 - questionId: {}, newStatus: {}", questionId, status);

        return QnAConverter.toQuestionDetail(qna);
    }

    @Override
    public QnAResponseDTO.AdminStatsResponse getStats() {
        long totalCount = qnaRepository.count();
        long pendingCount = qnaRepository.countByStatus(QnAStatus.PENDING);
        long answeredCount = qnaRepository.countByStatus(QnAStatus.ANSWERED);
        long closedCount = qnaRepository.countByStatus(QnAStatus.CLOSED);

        return QnAResponseDTO.AdminStatsResponse.builder()
                .totalCount(totalCount)
                .pendingCount(pendingCount)
                .answeredCount(answeredCount)
                .closedCount(closedCount)
                .build();
    }

    @Override
    public QnAResponseDTO.PendingCountResponse getPendingCount() {
        long count = qnaRepository.countByStatus(QnAStatus.PENDING);
        return QnAResponseDTO.PendingCountResponse.builder()
                .count(count)
                .build();
    }

    // ===== Helper Methods =====

    private QnA findQnAById(Long questionId) {
        return qnaRepository.findById(questionId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.QNA_NOT_FOUND));
    }
}

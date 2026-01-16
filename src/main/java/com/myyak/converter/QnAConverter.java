package com.myyak.converter;

import com.myyak.domain.QnA;
import com.myyak.domain.QnAReply;
import com.myyak.domain.User;
import com.myyak.web.dto.QnADTO.QnARequestDTO;
import com.myyak.web.dto.QnADTO.QnAResponseDTO;

import java.util.List;

public class QnAConverter {

    public static QnA toEntity(QnARequestDTO.CreateQuestion request, User user) {
        return QnA.builder()
                .user(user)
                .title(request.getTitle())
                .content(request.getContent())
                .build();
    }

    public static QnAReply toReplyEntity(QnARequestDTO.CreateReply request, QnA qna, boolean isAdmin, String adminName) {
        return QnAReply.builder()
                .qna(qna)
                .content(request.getContent())
                .isAdmin(isAdmin)
                .adminName(adminName)
                .build();
    }

    public static QnAReply toAdminReplyEntity(QnARequestDTO.AdminReply request, QnA qna) {
        return QnAReply.builder()
                .qna(qna)
                .content(request.getContent())
                .isAdmin(true)
                .adminName(request.getAdminName())
                .build();
    }

    public static QnAResponseDTO.QuestionSummary toQuestionSummary(QnA qna) {
        boolean hasAdminReply = qna.getReplies().stream()
                .anyMatch(QnAReply::getIsAdmin);

        return QnAResponseDTO.QuestionSummary.builder()
                .id(qna.getId())
                .title(qna.getTitle())
                .status(qna.getStatus())
                .statusLabel(qna.getStatus().getDescription())
                .replyCount(qna.getReplies().size())
                .createdAt(qna.getCreatedAt())
                .hasAdminReply(hasAdminReply)
                .build();
    }

    public static QnAResponseDTO.AdminQuestionSummary toAdminQuestionSummary(QnA qna) {
        boolean hasAdminReply = qna.getReplies().stream()
                .anyMatch(QnAReply::getIsAdmin);

        return QnAResponseDTO.AdminQuestionSummary.builder()
                .id(qna.getId())
                .title(qna.getTitle())
                .status(qna.getStatus())
                .statusLabel(qna.getStatus().getDescription())
                .replyCount(qna.getReplies().size())
                .createdAt(qna.getCreatedAt())
                .hasAdminReply(hasAdminReply)
                .userName(qna.getUser().getName())
                .userEmail(qna.getUser().getEmail())
                .build();
    }

    public static QnAResponseDTO.QuestionDetail toQuestionDetail(QnA qna) {
        List<QnAResponseDTO.ReplyInfo> replies = qna.getReplies().stream()
                .map(QnAConverter::toReplyInfo)
                .toList();

        return QnAResponseDTO.QuestionDetail.builder()
                .id(qna.getId())
                .title(qna.getTitle())
                .content(qna.getContent())
                .status(qna.getStatus())
                .statusLabel(qna.getStatus().getDescription())
                .createdAt(qna.getCreatedAt())
                .replies(replies)
                .build();
    }

    public static QnAResponseDTO.ReplyInfo toReplyInfo(QnAReply reply) {
        return QnAResponseDTO.ReplyInfo.builder()
                .id(reply.getId())
                .content(reply.getContent())
                .isAdmin(reply.getIsAdmin())
                .adminName(reply.getAdminName())
                .createdAt(reply.getCreatedAt())
                .build();
    }

    public static QnAResponseDTO.CreateResult toCreateResult(QnA qna) {
        return QnAResponseDTO.CreateResult.builder()
                .id(qna.getId())
                .title(qna.getTitle())
                .status(qna.getStatus())
                .createdAt(qna.getCreatedAt())
                .build();
    }

    public static QnAResponseDTO.ReplyResult toReplyResult(QnAReply reply) {
        return QnAResponseDTO.ReplyResult.builder()
                .id(reply.getId())
                .content(reply.getContent())
                .isAdmin(reply.getIsAdmin())
                .createdAt(reply.getCreatedAt())
                .build();
    }
}

package com.myyak.web.dto.QnADTO;

import com.myyak.domain.enums.QnAStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class QnAResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionSummary {
        private Long id;
        private String title;
        private QnAStatus status;
        private String statusLabel;
        private int replyCount;
        private LocalDateTime createdAt;
        private Boolean hasAdminReply;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionDetail {
        private Long id;
        private String title;
        private String content;
        private QnAStatus status;
        private String statusLabel;
        private LocalDateTime createdAt;
        private List<ReplyInfo> replies;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplyInfo {
        private Long id;
        private String content;
        private Boolean isAdmin;
        private String adminName;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionListResponse {
        private List<QuestionSummary> questions;
        private long totalCount;
        private int page;
        private int totalPages;
        private Boolean hasNext;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminQuestionSummary {
        private Long id;
        private String title;
        private QnAStatus status;
        private String statusLabel;
        private int replyCount;
        private LocalDateTime createdAt;
        private Boolean hasAdminReply;
        private String userName;
        private String userEmail;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminQuestionListResponse {
        private List<AdminQuestionSummary> questions;
        private long totalCount;
        private int page;
        private int totalPages;
        private Boolean hasNext;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminStatsResponse {
        private long totalCount;
        private long pendingCount;
        private long answeredCount;
        private long closedCount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingCountResponse {
        private long count;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateResult {
        private Long id;
        private String title;
        private QnAStatus status;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplyResult {
        private Long id;
        private String content;
        private Boolean isAdmin;
        private LocalDateTime createdAt;
    }
}

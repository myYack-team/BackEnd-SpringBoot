package com.myyak.web.dto.AdminDTO;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

public class AdminRequestDTO {

    /**
     * 영양제 목록 조회 요청
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class SupplementListRequest {
        private int page = 0;
        private int size = 10;
        private Integer days = 7;  // 최근 N일 (null이면 전체)
        private String search;      // 검색어
    }

    /**
     * AI 채팅 요청
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ChatRequest {
        @NotNull(message = "에러 로그 정보는 필수입니다")
        private ErrorLogContext errorLog;

        private List<ChatMessage> messages;

        private String userMessage;
    }

    /**
     * 에러 로그 컨텍스트 (채팅에 포함되는 에러 정보)
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ErrorLogContext {
        private String timestamp;
        private String level;
        private String logger;
        private String message;
        private String stackTrace;
    }

    /**
     * 채팅 메시지
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ChatMessage {
        private String role;  // "user" or "assistant"
        private String content;
    }
}

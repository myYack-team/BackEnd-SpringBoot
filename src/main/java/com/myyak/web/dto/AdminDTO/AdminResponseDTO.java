package com.myyak.web.dto.AdminDTO;

import com.myyak.domain.enums.SupplementTag;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class AdminResponseDTO {

    /**
     * 약물 데이터 통계 응답
     */
    @Getter
    @Builder
    public static class DrugStats {
        private long totalCount;           // 전체 약물 수
        private long withoutEfficacy;      // 효능 미수집 수
        private long withoutIngredient;    // 성분명 미파싱 수
    }

    /**
     * 최근 등록 영양제 목록 응답
     */
    @Getter
    @Builder
    public static class SupplementList {
        private List<SupplementItem> supplements;
        private int page;
        private int size;
        private int totalPages;
        private long totalElements;
    }

    /**
     * 영양제 아이템
     */
    @Getter
    @Builder
    public static class SupplementItem {
        private Long id;
        private String name;
        private SupplementTag tag;
        private String tagDescription;
        private int selectionCount;
        private LocalDateTime createdAt;
        private String createdByName;
    }

    /**
     * 태그별 영양제 통계
     */
    @Getter
    @Builder
    public static class SupplementTagStats {
        private Map<SupplementTag, Long> tagCounts;
        private long totalCount;
    }

    /**
     * 가입자 통계 응답
     */
    @Getter
    @Builder
    public static class UserStats {
        private long total;
        private long today;
        private long week;
        private long month;
        private Map<String, Long> byGender;
        private Map<String, Long> byAgeGroup;
        private Map<String, Long> bySignupPurpose;
    }

    /**
     * 일별 가입 추이
     */
    @Getter
    @Builder
    public static class DailySignups {
        private List<DailyCount> dailyCounts;
    }

    @Getter
    @Builder
    public static class DailyCount {
        private LocalDate date;
        private long count;
    }

    /**
     * 영양제 삭제 결과
     */
    @Getter
    @Builder
    public static class SupplementDeleteResult {
        private Long deletedSupplementId;
        private int deletedUserSupplementCount;
    }

    /**
     * 서버 헬스 상태 응답
     */
    @Getter
    @Builder
    public static class HealthStatus {
        private boolean serverUp;
        private boolean databaseUp;
        private boolean storageUp;
        private long responseTimeMs;
        private String storageProvider;

        private long heapUsedMb;
        private long heapMaxMb;
        private double cpuUsage;

        private String appVersion;
        private String buildTime;

        private LocalDateTime checkedAt;
    }

    /**
     * 에러 로그 목록 응답
     */
    @Getter
    @Builder
    public static class ErrorLogList {
        private List<ErrorLogItem> logs;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    /**
     * 에러 로그 아이템 (이벤트 그룹)
     * 같은 스레드에서 1초 이내에 발생한 로그들을 하나의 이벤트로 묶음
     */
    @Getter
    @Builder
    public static class ErrorLogItem {
        private String id;
        private LocalDateTime timestamp;      // 이벤트 시작 시간 (첫 로그 시간)
        private String level;                 // 대표 레벨 (가장 심각한 레벨)
        private String logger;
        private String message;               // 대표 메시지 (첫 번째 또는 ERROR 메시지)
        private String stackTrace;
        private String threadName;
        private int occurrenceCount;          // 동일 로그 발생 횟수
        private int relatedLogCount;          // 이벤트 내 관련 로그 수
        private List<RelatedLog> relatedLogs; // 이벤트 내 관련 로그 목록
    }

    /**
     * 이벤트 내 관련 로그
     */
    @Getter
    @Builder
    public static class RelatedLog {
        private LocalDateTime timestamp;
        private String level;
        private String logger;
        private String message;
        private String stackTrace;
    }

    /**
     * AI 채팅 응답
     */
    @Getter
    @Builder
    public static class ChatResponse {
        private String response;
        private LocalDateTime timestamp;
    }
}

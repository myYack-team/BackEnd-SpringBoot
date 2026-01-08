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
}

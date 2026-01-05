package com.myyak.web.dto.AnalysisDTO;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 약물 분석 응답 DTO
 */
public class AnalysisResponseDTO {

    /**
     * 분석 결과 (분석 요청 응답)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisResult {
        private Long reportId;
        private LocalDateTime analysisDate;
        private List<MechanismGroup> mechanismGroups;
        private List<FoodInteractionSummary> foodInteractions;
        private QuotaInfo quota;
    }

    /**
     * 기전 카드 (작용 메커니즘 그룹)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MechanismGroup {
        private String categoryName;       // "혈압 조절", "당뇨 조절" 등
        private String categoryIcon;       // 이모지 아이콘 (❤️, 💊 등)
        private String description;        // 2~3문장 설명
        private String analogy;            // 비유 1줄
        private Integer medicationCount;   // 관련 약물 개수
        private List<MechanismMedication> medications;  // 관련 약물 목록
    }

    /**
     * 기전 그룹 내 약물 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MechanismMedication {
        private String name;               // 약물명
        private String ingredientName;     // 성분명
    }

    /**
     * 음식 상호작용 요약 (목록 표시용)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FoodInteractionSummary {
        private String foodName;                    // 음식명
        private String foodIcon;                    // 이모지 아이콘
        private String interactionLevel;            // HIGH, MEDIUM, LOW
        private Integer affectedMedicationCount;    // 영향받는 약물 개수
        private String summaryReason;               // 요약 이유 (1줄)
        private List<FoodInteractionDetail> details; // 상세 (클릭 시)
    }

    /**
     * 음식 상호작용 상세 (개별 약물별)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FoodInteractionDetail {
        private Long medicationId;         // 약물 ID
        private String medicationName;     // 약물명
        private String reason;             // 상세 이유
    }

    /**
     * 레포트 목록
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportList {
        private List<ReportSummary> reports;
        private Integer totalCount;
    }

    /**
     * 레포트 요약 (목록 아이템)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportSummary {
        private Long id;
        private LocalDateTime analysisDate;
        private Integer mechanismGroupCount;     // 약물 효과 개수
        private Integer foodInteractionCount;    // 주의 음식 개수
    }

    /**
     * 분석 쿼터 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuotaInfo {
        private Integer monthlyLimit;
        private Integer usedCount;
        private Integer remainingCount;
        private String resetDate;
    }
}

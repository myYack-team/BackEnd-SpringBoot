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
        private List<FoodSuggestion> foodSuggestions;
        private List<SupplementInteraction> supplementInteractions;
        private List<LifestyleTip> lifestyleTips;
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

    // ===== 신규 기능: 음식 제안, 영양제 상호작용, 생활 팁 =====

    /**
     * 음식 제안 (복용 약물과 좋은 궁합의 음식)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FoodSuggestion {
        private String foodName;      // 음식명
        private String foodIcon;      // 이모지 아이콘
        private String reason;        // 추천 이유 (1줄)
        private String tip;           // 섭취 팁 (선택)
        private List<RelatedMedication> relatedMedications;  // 관련 약물 목록
    }

    /**
     * 영양제 상호작용 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplementInteraction {
        private String supplementName;    // 영양제명
        private String supplementTag;     // 영양제 태그 (VITAMIN_C, OMEGA_3 등)
        private String interactionLevel;  // GOOD, TIMING, CAUTION
        private String summaryReason;     // 요약 이유 (1줄)
        private String source;            // 출처 (선택)
        private List<SupplementDetail> details;  // 상세 정보
    }

    /**
     * 영양제 상호작용 상세 (개별 약물별)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplementDetail {
        private String medicationName;  // 약물명
        private String reason;          // 상세 이유
    }

    /**
     * 생활 팁 (복용 약물 관련 생활 습관 조언)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LifestyleTip {
        private String category;              // 카테고리 코드 (EXERCISE, SLEEP, DIET 등)
        private String categoryIcon;          // 카테고리 이모지
        private String categoryLabel;         // 카테고리 라벨 (운동, 수면, 식이 등)
        private String title;                 // 팁 제목
        private String tip;                   // 팁 내용 (1~2줄)
        private String detailedExplanation;   // 상세 설명
        private String source;                // 출처 (필수)
        private List<RelatedMedication> relatedMedications;  // 관련 약물 목록
    }

    /**
     * 관련 약물 정보 (음식 제안, 생활 팁에서 공통 사용)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedMedication {
        private String name;    // 약물명
        private String detail;  // 추가 설명 (선택)
    }
}

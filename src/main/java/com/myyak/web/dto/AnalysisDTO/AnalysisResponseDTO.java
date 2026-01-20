package com.myyak.web.dto.AnalysisDTO;

import lombok.*;

import java.time.LocalDate;
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
        private List<LifestyleTip> lifestyleTips;
        private PatternAnalysis patternAnalysis;  // 패턴 분석 결과
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

    // ===== 패턴 분석 관련 DTO =====

    /**
     * 패턴 분석 결과 (메인)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatternAnalysis {
        private LocalDate analysisStartDate;              // 분석 시작 날짜
        private LocalDate analysisEndDate;                // 분석 종료 날짜
        private AdherenceAnalysis adherenceAnalysis;      // 복약 순응도 분석
        private List<Pattern> patterns;                   // 발견된 패턴 목록
        private List<Insight> insights;                   // 인사이트 목록
        private PatternSummary summary;                   // 요약
        private List<DailyCondition> dailyConditions;     // 일별 컨디션 (그래프용)
        private List<TimelineEvent> events;               // 타임라인 이벤트
    }

    /**
     * 복약 순응도 분석
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdherenceAnalysis {
        private Double overallRate;                   // 전체 복약률 (0~100)
        private WeekdayPattern weekdayPattern;        // 요일별 패턴
        private TimingPattern timingPattern;          // 시간대별 패턴
        private Integer missedDays;                   // 복용 누락 일수
        private Integer perfectDays;                  // 완벽 복용 일수
    }

    /**
     * 요일별 패턴
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeekdayPattern {
        private Double mondayRate;
        private Double tuesdayRate;
        private Double wednesdayRate;
        private Double thursdayRate;
        private Double fridayRate;
        private Double saturdayRate;
        private Double sundayRate;
        private String bestDay;           // 가장 복약률이 높은 요일
        private String worstDay;          // 가장 복약률이 낮은 요일
    }

    /**
     * 시간대별 패턴
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimingPattern {
        private Double morningRate;       // 아침 복약률
        private Double lunchRate;         // 점심 복약률
        private Double dinnerRate;        // 저녁 복약률
        private Double bedtimeRate;       // 취침 전 복약률
        private String bestTiming;        // 가장 복약률이 높은 시간대
        private String worstTiming;       // 가장 복약률이 낮은 시간대
    }

    /**
     * 발견된 패턴
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pattern {
        private String patternType;       // POSITIVE, NEGATIVE, NEUTRAL
        private String patternIcon;       // 이모지 아이콘
        private String title;             // 패턴 제목
        private String description;       // 패턴 설명
        private String suggestion;        // 개선 제안 (선택)
    }

    /**
     * 인사이트 (분석 결과에서 도출된 통찰)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Insight {
        private String insightType;       // CONDITION_CORRELATION, HABIT_SUGGESTION, ACHIEVEMENT
        private String insightIcon;       // 이모지 아이콘
        private String title;             // 인사이트 제목
        private String description;       // 인사이트 설명
        private String actionItem;        // 실천 항목 (선택)
    }

    /**
     * 패턴 분석 요약
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatternSummary {
        private String overallAssessment;     // 전반적인 평가 (1~2문장)
        private String positivePoint;         // 긍정적인 점
        private String improvementPoint;      // 개선이 필요한 점
        private String encouragement;         // 격려 메시지
    }

    /**
     * 일별 컨디션 (그래프 데이터용)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyCondition {
        private LocalDate date;           // 날짜
        private Integer conditionScore;   // 컨디션 점수 (0~10)
        private Double adherenceRate;     // 해당 일 복약률 (0~100)
        private Boolean hasNote;          // 메모 존재 여부
    }

    /**
     * 타임라인 이벤트 (주요 변화 포인트)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineEvent {
        private LocalDate date;           // 이벤트 날짜
        private String eventType;         // CONDITION_CHANGE, ADHERENCE_CHANGE, MEDICATION_CHANGE
        private String eventIcon;         // 이모지 아이콘
        private String title;             // 이벤트 제목
        private String description;       // 이벤트 설명
    }
}

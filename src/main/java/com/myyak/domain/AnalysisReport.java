package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 약물 분석 레포트 엔티티
 * 사용자별 분석 결과를 저장하여 이후 조회 가능
 */
@Entity
@Table(name = "analysis_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AnalysisReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime analysisDate;     // 분석 일시

    @Column(nullable = false)
    private Integer mechanismGroupCount;    // 기전 카드 개수 (약물 효과 종류)

    @Column(nullable = false)
    private Integer foodInteractionCount;   // 음식 상호작용 개수 (주의 음식)

    @Column(columnDefinition = "JSON")
    private String medicationSnapshot;      // 분석 당시 약물 목록 스냅샷
    /*
    [
        {"itemSeq": "200001234", "displayName": "리피토정", "ingredientKr": "아토르바스타틴"},
        {"itemSeq": "200005678", "displayName": "노바스크정", "ingredientKr": "암로디핀"}
    ]
    */

    @Column(columnDefinition = "JSON", nullable = false)
    private String llmResponse;             // LLM 원본 응답 (기전 카드 + 음식 병용 정보)
    /*
    {
        "mechanismGroups": [...],
        "foodInteractions": [...]
    }
    */

    // ===== 패턴 분석 관련 필드 =====

    @Column(columnDefinition = "JSON")
    private String patternAnalysis;         // 패턴 분석 결과 JSON
    /*
    {
        "adherenceAnalysis": {...},
        "patterns": [...],
        "insights": [...],
        "summary": {...},
        "dailyConditions": [...],
        "events": [...]
    }
    */

    @Column
    private LocalDate analysisStartDate;    // 패턴 분석 시작 날짜 (30일 전)

    @Column
    private LocalDate analysisEndDate;      // 패턴 분석 종료 날짜 (분석 당일)

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isPreviewData;          // 테스트 분석 여부 (Mock 데이터 사용)
}

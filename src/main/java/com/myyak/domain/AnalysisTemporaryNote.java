package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * AI 분석용 임시 건강 메모 엔티티
 * 사용자가 분석 요청 전 추가로 입력하는 컨디션/증상 정보
 */
@Entity
@Table(name = "analysis_temporary_notes",
        indexes = @Index(name = "idx_analysis_temp_note_user_date", columnList = "user_id, note_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AnalysisTemporaryNote extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "note_date", nullable = false)
    private LocalDate noteDate;

    @Column(nullable = false)
    private Integer conditionScore;  // 0~10

    @Column(columnDefinition = "TEXT", length = 500)
    private String symptoms;  // JSON array string

    @Column(columnDefinition = "TEXT", length = 500)
    private String additionalNote;
}

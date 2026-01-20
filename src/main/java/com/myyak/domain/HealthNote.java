package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 건강 메모 엔티티
 * 사용자가 매일 기록하는 컨디션 점수와 메모
 */
@Entity
@Table(name = "health_notes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_health_note_user_date",
                columnNames = {"user_id", "note_date"}
        ),
        indexes = @Index(
                name = "idx_health_note_user_date",
                columnList = "user_id, note_date"
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class HealthNote extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "note_date", nullable = false)
    private LocalDate noteDate;

    @Column(nullable = false)
    @Builder.Default
    private Integer conditionScore = 10;  // 0~10, default 10

    @Column(columnDefinition = "TEXT", length = 500)
    private String content;

    /**
     * 컨디션 점수 업데이트
     */
    public void updateConditionScore(Integer conditionScore) {
        if (conditionScore != null) {
            this.conditionScore = conditionScore;
        }
    }

    /**
     * 메모 내용 업데이트
     */
    public void updateContent(String content) {
        this.content = content;
    }

    /**
     * 전체 업데이트
     */
    public void update(Integer conditionScore, String content) {
        if (conditionScore != null) {
            this.conditionScore = conditionScore;
        }
        this.content = content;
    }
}

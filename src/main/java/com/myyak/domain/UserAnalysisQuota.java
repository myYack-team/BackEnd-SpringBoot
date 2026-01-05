package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 사용자 AI 분석 횟수 관리 엔티티
 * 월별 분석 횟수 제한 (향후 유료결제 도입 대비)
 */
@Entity
@Table(name = "user_analysis_quota")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserAnalysisQuota extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private Integer monthlyLimit = 3;       // 월간 제한 (기본 3회)

    @Column(nullable = false)
    @Builder.Default
    private Integer usedCount = 0;          // 사용 횟수

    @Column(nullable = false)
    private LocalDate resetDate;            // 다음 리셋 날짜 (매월 1일)

    /**
     * 남은 분석 횟수 계산
     */
    public Integer getRemainingCount() {
        return Math.max(0, monthlyLimit - usedCount);
    }

    /**
     * 분석 가능 여부 확인
     */
    public boolean canAnalyze() {
        return usedCount < monthlyLimit;
    }

    /**
     * 분석 횟수 증가
     */
    public void incrementUsedCount() {
        this.usedCount++;
    }

    /**
     * 월별 리셋 (매월 1일)
     */
    public void resetMonthlyQuota(LocalDate newResetDate) {
        this.usedCount = 0;
        this.resetDate = newResetDate;
    }

    /**
     * 리셋 필요 여부 확인
     */
    public boolean needsReset(LocalDate today) {
        return today.isAfter(resetDate) || today.isEqual(resetDate);
    }
}

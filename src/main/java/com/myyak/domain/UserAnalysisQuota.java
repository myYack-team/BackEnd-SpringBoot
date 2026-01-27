package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 사용자 AI 분석 횟수 관리 엔티티
 * 월간 분석 횟수 제한
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
    private Integer monthlyUsedCount = 0;   // 월간 사용 횟수

    @Column
    private LocalDate monthlyResetDate;     // 월간 리셋 날짜 (매월 1일)

    /**
     * 남은 월간 분석 횟수 계산
     */
    public Integer getMonthlyRemainingCount(int monthlyLimit) {
        return Math.max(0, monthlyLimit - monthlyUsedCount);
    }

    /**
     * 이번 달 분석 가능 여부 확인
     */
    public boolean canAnalyzeThisMonth(int monthlyLimit) {
        return monthlyUsedCount < monthlyLimit;
    }

    /**
     * 월간 분석 횟수 증가
     */
    public void incrementMonthlyUsedCount() {
        this.monthlyUsedCount++;
    }

    /**
     * 월간 리셋 (매월 1일)
     */
    public void resetMonthlyQuota(LocalDate nextMonth) {
        this.monthlyUsedCount = 0;
        this.monthlyResetDate = nextMonth;
    }

    /**
     * 월간 리셋 필요 여부 확인
     */
    public boolean needsMonthlyReset(LocalDate today) {
        return monthlyResetDate == null || !today.isBefore(monthlyResetDate);
    }
}

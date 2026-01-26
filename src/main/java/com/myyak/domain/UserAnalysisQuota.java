package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 사용자 AI 분석 횟수 관리 엔티티
 * 월간 분석 횟수 제한 (향후 유료결제 도입 대비)
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

    // ===== 주간 쿼터 (하위 호환성 유지) =====

    @Column(nullable = false)
    @Builder.Default
    private Integer weeklyLimit = 3;        // 주간 제한 (기본 3회)

    @Column(nullable = false)
    @Builder.Default
    private Integer weeklyUsedCount = 0;    // 주간 사용 횟수

    @Column
    private LocalDate weeklyResetDate;      // 다음 리셋 날짜 (매주 월요일)

    // ===== 월간 쿼터 =====

    @Column(nullable = false)
    @Builder.Default
    private Integer monthlyLimit = 2;       // 월간 제한 (기본 2회)

    @Column(nullable = false)
    @Builder.Default
    private Integer monthlyUsedCount = 0;   // 월간 사용 횟수

    @Column
    private LocalDate monthlyResetDate;     // 월간 리셋 날짜 (매월 1일)

    // ===== 주간 메서드 (하위 호환성 유지) =====

    /**
     * 남은 주간 분석 횟수 계산
     */
    public Integer getWeeklyRemainingCount() {
        return Math.max(0, weeklyLimit - weeklyUsedCount);
    }

    /**
     * 이번 주 분석 가능 여부 확인
     */
    public boolean canAnalyzeThisWeek() {
        return weeklyUsedCount < weeklyLimit;
    }

    /**
     * 주간 분석 횟수 증가
     */
    public void incrementWeeklyUsedCount() {
        this.weeklyUsedCount++;
    }

    /**
     * 주간 리셋 (매주 월요일)
     */
    public void resetWeeklyQuota(LocalDate nextMonday) {
        this.weeklyUsedCount = 0;
        this.weeklyResetDate = nextMonday;
    }

    /**
     * 주간 리셋 필요 여부 확인
     */
    public boolean needsWeeklyReset(LocalDate today) {
        return weeklyResetDate == null || !today.isBefore(weeklyResetDate);
    }

    // ===== 월간 메서드 =====

    /**
     * 남은 월간 분석 횟수 계산
     */
    public Integer getMonthlyRemainingCount() {
        return Math.max(0, monthlyLimit - monthlyUsedCount);
    }

    /**
     * 이번 달 분석 가능 여부 확인
     */
    public boolean canAnalyzeThisMonth() {
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

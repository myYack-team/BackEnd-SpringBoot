package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import com.myyak.domain.enums.MedicationTiming;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "reminders", indexes = {
    @Index(name = "idx_reminder_user_med", columnList = "user_medication_id"),
    @Index(name = "idx_reminder_user_supp", columnList = "user_supplement_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Reminder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_medication_id")
    private UserMedication userMedication;  // 의약품 알림 (nullable)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_supplement_id")
    private UserSupplement userSupplement;  // 영양제 알림 (nullable)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MedicationTiming timing;  // 아침 식전, 아침 식후, 점심 식전 등

    @Column(nullable = false)
    private LocalTime time;  // 실제 알림 시간

    @Builder.Default
    private Boolean enabled = true;

    // 스누즈(다시 알림) 시간 - null이면 스누즈 없음
    private LocalDateTime snoozeUntil;

    public void updateTime(LocalTime time) {
        this.time = time;
        this.timing = MedicationTiming.fromTime(time);
    }

    public void toggle() {
        this.enabled = !this.enabled;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    /**
     * 스누즈 설정 (다시 알림)
     * @param minutes 몇 분 후에 다시 알림 (10, 30, 60)
     */
    public void snooze(int minutes) {
        this.snoozeUntil = LocalDateTime.now().plusMinutes(minutes);
    }

    /**
     * 스누즈 해제
     */
    public void clearSnooze() {
        this.snoozeUntil = null;
    }

    /**
     * 현재 스누즈 상태인지 확인
     */
    public boolean isSnoozed() {
        return snoozeUntil != null && LocalDateTime.now().isBefore(snoozeUntil);
    }

    /**
     * 의약품 알림인지 확인
     */
    public boolean isMedicationReminder() {
        return userMedication != null;
    }

    /**
     * 영양제 알림인지 확인
     */
    public boolean isSupplementReminder() {
        return userSupplement != null;
    }
}

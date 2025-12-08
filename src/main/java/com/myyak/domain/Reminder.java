package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import com.myyak.domain.enums.MedicationTiming;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(name = "reminders", indexes = {
    @Index(name = "idx_reminder_user_med", columnList = "user_medication_id")
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
    @JoinColumn(name = "user_medication_id", nullable = false)
    private UserMedication userMedication;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MedicationTiming timing;  // 아침 식전, 아침 식후, 점심 식전 등

    @Column(nullable = false)
    private LocalTime time;  // 실제 알림 시간

    @Builder.Default
    private Boolean enabled = true;

    public void updateTime(LocalTime time) {
        this.time = time;
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
}

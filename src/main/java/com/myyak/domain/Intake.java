package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import com.myyak.domain.enums.IntakeStatus;
import com.myyak.domain.enums.MedicationTiming;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "intakes", indexes = {
    @Index(name = "idx_intake_user_med", columnList = "user_medication_id"),
    @Index(name = "idx_intake_user_supp", columnList = "user_supplement_id"),
    @Index(name = "idx_intake_taken_at", columnList = "takenAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Intake extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_medication_id")
    private UserMedication userMedication;  // 의약품 복용 기록 (nullable)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_supplement_id")
    private UserSupplement userSupplement;  // 영양제 복용 기록 (nullable)

    @Column(nullable = false)
    private LocalDateTime takenAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MedicationTiming timing;  // 어떤 복용 시점에 복용했는지

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private IntakeStatus status = IntakeStatus.TAKEN;

    public void updateStatus(IntakeStatus status) {
        this.status = status;
    }

    /**
     * 의약품 복용 기록인지 확인
     */
    public boolean isMedicationIntake() {
        return userMedication != null;
    }

    /**
     * 영양제 복용 기록인지 확인
     */
    public boolean isSupplementIntake() {
        return userSupplement != null;
    }
}

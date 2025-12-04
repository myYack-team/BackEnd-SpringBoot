package com.myyak.domain.intake.entity;

import com.myyak.domain.common.BaseEntity;
import com.myyak.domain.intake.entity.enums.IntakeStatus;
import com.myyak.domain.medication.entity.Medication;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "intakes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Intake extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(nullable = false)
    private LocalDateTime takenAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private IntakeStatus status = IntakeStatus.TAKEN;

    public void updateStatus(IntakeStatus status) {
        this.status = status;
    }
}

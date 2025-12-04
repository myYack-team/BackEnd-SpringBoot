package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "medications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Medication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String dosage;

    @Column(nullable = false)
    private Integer frequency;

    @Column(nullable = false)
    private Integer durationDays;

    @Column(nullable = false)
    private Integer totalCount;

    @Column(nullable = false)
    private Integer remainingCount;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @Builder.Default
    private Boolean isActive = true;

    public void decreaseRemainingCount(int count) {
        this.remainingCount = Math.max(0, this.remainingCount - count);
    }

    public void updateMedication(String name, String dosage, Integer frequency,
                                  Integer durationDays, Integer totalCount, Integer remainingCount) {
        this.name = name;
        this.dosage = dosage;
        this.frequency = frequency;
        this.durationDays = durationDays;
        this.totalCount = totalCount;
        this.remainingCount = remainingCount;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public boolean isLowStock() {
        int dailyDose = calculateDailyDose();
        return this.remainingCount <= dailyDose * 3;
    }

    private int calculateDailyDose() {
        String numStr = dosage.replaceAll("[^0-9]", "");
        int dosePerTime = numStr.isEmpty() ? 1 : Integer.parseInt(numStr);
        return dosePerTime * frequency;
    }
}

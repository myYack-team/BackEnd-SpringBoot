package com.myyak.domain.medication.entity;

import com.myyak.domain.common.BaseEntity;
import com.myyak.domain.user.entity.User;
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
    private String dosage; // 1회 복용량 (예: "1정", "2캡슐")

    @Column(nullable = false)
    private Integer frequency; // 1일 복용 횟수

    @Column(nullable = false)
    private Integer durationDays; // 처방 일수

    @Column(nullable = false)
    private Integer totalCount; // 총 개수

    @Column(nullable = false)
    private Integer remainingCount; // 남은 개수

    @Column(nullable = false)
    private LocalDate startDate; // 복용 시작일

    private LocalDate endDate; // 복용 종료일

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
        // 3일치 이하 남았는지 확인
        int dailyDose = calculateDailyDose();
        return this.remainingCount <= dailyDose * 3;
    }

    private int calculateDailyDose() {
        // dosage에서 숫자 추출 (예: "1정" -> 1)
        String numStr = dosage.replaceAll("[^0-9]", "");
        int dosePerTime = numStr.isEmpty() ? 1 : Integer.parseInt(numStr);
        return dosePerTime * frequency;
    }
}

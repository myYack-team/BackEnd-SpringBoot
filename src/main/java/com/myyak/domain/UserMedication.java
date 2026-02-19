package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 사용자-약물 연결 테이블
 * 사용자의 복용 정보 (복용량, 횟수, 기간, 재고 등)
 */
@Entity
@Table(name = "user_medications", indexes = {
    @Index(name = "idx_user_med_user", columnList = "user_id"),
    @Index(name = "idx_user_med_drug", columnList = "drug_item_seq"),
    @Index(name = "idx_user_med_active", columnList = "isActive"),
    @Index(name = "idx_user_med_prescription", columnList = "prescription_id")
})
@Setter
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserMedication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drug_item_seq")
    private DrugInfo drugInfo;  // 약물 마스터 정보 (null 가능 - API에 없는 약)

    // DrugInfo가 없을 경우 직접 입력한 약 이름
    @Column(length = 200)
    private String customDrugName;

    // 복용량 (예: "1", "2", "0.5")
    @Column(nullable = false, length = 20)
    private String dosage;

    // 하루 복용 횟수
    @Column(nullable = false)
    private Integer frequency;

    // 총 복용 일수
    @Column(nullable = false)
    private Integer durationDays;

    // 총 약 개수
    @Column(nullable = false)
    private Integer totalCount;

    // 남은 약 개수
    @Column(nullable = false)
    private Integer remainingCount;

    // 복용 시작일
    @Column(nullable = false)
    private LocalDate startDate;

    // 복용 종료 예정일
    private LocalDate endDate;

    // 활성 상태
    @Builder.Default
    private Boolean isActive = true;

    // 메모 (사용자가 추가로 기록하고 싶은 내용)
    @Column(length = 500)
    private String memo;

    // 처방전 ID (처방전과 연결)
    @Column(name = "prescription_id")
    private Long prescriptionId;

    /**
     * 약 이름 반환 (DrugInfo가 있으면 그것을, 없으면 customDrugName)
     */
    public String getDrugName() {
        if (drugInfo != null) {
            return drugInfo.getItemName();
        }
        return customDrugName;
    }

    public void decreaseRemainingCount(int count) {
        this.remainingCount = Math.max(0, this.remainingCount - count);
    }

    public void updateMedication(String dosage, Integer frequency,
                                  Integer durationDays, Integer totalCount,
                                  Integer remainingCount, String memo) {
        this.dosage = dosage;
        this.frequency = frequency;
        this.durationDays = durationDays;
        this.totalCount = totalCount;
        this.remainingCount = remainingCount;
        this.memo = memo;
    }

    public void deactivate() {
        this.isActive = false;
        LocalDate yesterday = LocalDate.now().minusDays(1);
        if (this.endDate == null || this.endDate.isAfter(yesterday)) {
            this.endDate = yesterday;
        }
    }

    public boolean isLowStock() {
        int dailyDose = calculateDailyDose();
        return this.remainingCount <= dailyDose * 3;
    }

    private int calculateDailyDose() {
        String numStr = dosage.replaceAll("[^0-9.]", "");
        double dosePerTime = numStr.isEmpty() ? 1 : Double.parseDouble(numStr);
        return (int) Math.ceil(dosePerTime * frequency);
    }
}

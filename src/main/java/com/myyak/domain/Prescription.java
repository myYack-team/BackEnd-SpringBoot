package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 처방전 정보
 * 처방전 이미지와 관련 메타데이터를 저장
 */
@Entity
@Table(name = "prescriptions", indexes = {
    @Index(name = "idx_prescription_user", columnList = "user_id"),
    @Index(name = "idx_prescription_date", columnList = "prescription_date")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Prescription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 처방전 이미지 URL (로컬 경로 또는 S3 URL)
    @Column(nullable = false, length = 500)
    private String imageUrl;

    // 처방 날짜
    @Column(name = "prescription_date", nullable = false)
    private LocalDate prescriptionDate;

    // 병원명 (선택)
    @Column(length = 200)
    private String hospitalName;

    // 메모 (선택)
    @Column(length = 500)
    private String notes;

    /**
     * 처방전 정보 업데이트
     */
    public void update(LocalDate prescriptionDate, String hospitalName, String notes) {
        if (prescriptionDate != null) {
            this.prescriptionDate = prescriptionDate;
        }
        this.hospitalName = hospitalName;
        this.notes = notes;
    }

    /**
     * 정적 팩토리 메서드
     */
    public static Prescription create(User user, String imageUrl, LocalDate prescriptionDate) {
        return Prescription.builder()
                .user(user)
                .imageUrl(imageUrl)
                .prescriptionDate(prescriptionDate)
                .build();
    }
}

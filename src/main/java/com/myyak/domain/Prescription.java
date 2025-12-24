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

    // 환자명 (OCR 인식 or 로그인 사용자명)
    @Column(length = 100)
    private String patientName;

    // 병원명 (선택)
    @Column(length = 200)
    private String hospitalName;

    // 진단명/증상 (OCR 인식 or AI 추론)
    @Column(length = 500)
    private String diagnosis;

    // 복용 기간 (일)
    private Integer durationDays;

    // 복용 종료일 (처방일 + 복용기간)
    @Column(name = "end_date")
    private LocalDate endDate;

    // 메모 (선택)
    @Column(length = 500)
    private String notes;

    /**
     * 처방전 정보 업데이트
     */
    public void update(LocalDate prescriptionDate, String patientName, String hospitalName,
                       String diagnosis, Integer durationDays, String notes) {
        if (prescriptionDate != null) {
            this.prescriptionDate = prescriptionDate;
        }
        this.patientName = patientName;
        this.hospitalName = hospitalName;
        this.diagnosis = diagnosis;
        this.durationDays = durationDays;
        this.notes = notes;

        // 복용 종료일 계산
        if (prescriptionDate != null && durationDays != null && durationDays > 0) {
            this.endDate = prescriptionDate.plusDays(durationDays - 1);
        }
    }

    /**
     * 스캔 결과로 처방전 정보 업데이트
     */
    public void updateFromScan(String patientName, String hospitalName, String diagnosis, Integer durationDays) {
        this.patientName = patientName;
        this.hospitalName = hospitalName;
        this.diagnosis = diagnosis;
        this.durationDays = durationDays;

        // 복용 종료일 계산
        if (this.prescriptionDate != null && durationDays != null && durationDays > 0) {
            this.endDate = this.prescriptionDate.plusDays(durationDays - 1);
        }
    }

    /**
     * 복용 상태 확인
     */
    public String getStatus() {
        LocalDate today = LocalDate.now();
        if (today.isBefore(prescriptionDate)) {
            return "UPCOMING";  // 복용 예정
        }
        if (endDate != null && today.isAfter(endDate)) {
            return "COMPLETED"; // 복용 완료
        }
        return "IN_PROGRESS";   // 복용 중
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

    /**
     * 정적 팩토리 메서드 (확장)
     */
    public static Prescription create(User user, String imageUrl, LocalDate prescriptionDate,
                                       String patientName, String hospitalName, String diagnosis,
                                       Integer durationDays) {
        LocalDate endDate = null;
        if (prescriptionDate != null && durationDays != null && durationDays > 0) {
            endDate = prescriptionDate.plusDays(durationDays - 1);
        }

        return Prescription.builder()
                .user(user)
                .imageUrl(imageUrl)
                .prescriptionDate(prescriptionDate)
                .patientName(patientName)
                .hospitalName(hospitalName)
                .diagnosis(diagnosis)
                .durationDays(durationDays)
                .endDate(endDate)
                .build();
    }
}

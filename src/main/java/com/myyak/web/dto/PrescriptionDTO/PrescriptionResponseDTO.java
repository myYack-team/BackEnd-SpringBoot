package com.myyak.web.dto.PrescriptionDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class PrescriptionResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrescriptionInfo {
        private Long id;
        private String imageUrl;
        private LocalDate prescriptionDate;
        private String patientName;       // 환자명
        private String hospitalName;
        private String doctorName;        // 처방의사
        private String diagnosis;         // 진단명/증상
        private Integer durationDays;     // 복용 기간 (일)
        private LocalDate endDate;        // 복용 종료일
        private String status;            // 복용 상태 (UPCOMING, IN_PROGRESS, COMPLETED)
        private String notes;
        private Integer medicationCount;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrescriptionDetail {
        private Long id;
        private String imageUrl;
        private LocalDate prescriptionDate;
        private String patientName;       // 환자명
        private String hospitalName;
        private String doctorName;        // 처방의사
        private String diagnosis;         // 진단명/증상
        private Integer durationDays;     // 복용 기간 (일)
        private LocalDate endDate;        // 복용 종료일
        private String status;            // 복용 상태 (UPCOMING, IN_PROGRESS, COMPLETED)
        private String notes;
        private List<MedicationSummary> medications;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MedicationSummary {
        private Long id;
        private String drugName;
        private String displayName;       // 표시용 이름
        private String imageUrl;
        private String dosage;
        private Integer frequency;
        private Integer durationDays;     // 복용 일수
        private Integer remainingCount;   // 남은 약 개수
        private Integer daysLeft;         // 남은 복용 일수
        private List<ReminderInfo> reminders;  // 알림 시간 목록
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReminderInfo {
        private Long id;
        private String time;              // HH:mm 형식
        private Boolean enabled;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrescriptionList {
        private List<PrescriptionInfo> prescriptions;
        private Integer totalCount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadResult {
        private Long prescriptionId;
        private String imageUrl;
        private LocalDate prescriptionDate;
    }

    /**
     * 처방전 + 약물 일괄 등록 결과
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterResult {
        private Long prescriptionId;
        private String imageUrl;
        private LocalDate prescriptionDate;
        private List<RegisteredMedication> medications;
        private Integer totalMedicationCount;
    }

    /**
     * 등록된 약물 정보 (일괄 등록 결과용)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisteredMedication {
        private Long id;
        private String drugName;
        private Integer dosage;
        private Integer frequency;
        private Integer durationDays;
    }
}

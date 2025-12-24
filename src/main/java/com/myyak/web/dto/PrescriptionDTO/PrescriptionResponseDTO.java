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
        private String imageUrl;
        private String dosage;
        private Integer frequency;
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
}

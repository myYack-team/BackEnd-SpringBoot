package com.myyak.web.dto.PrescriptionDTO;

import com.myyak.domain.enums.MedicationTiming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class PrescriptionRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class UpdateRequest {
        private LocalDate prescriptionDate;
        private String patientName;      // 환자명
        private String hospitalName;
        private String doctorName;       // 처방의사
        private String diagnosis;        // 진단명
        private Integer durationDays;    // 복용 기간 (일)
        private String notes;
    }

    /**
     * 처방전 + 약물 일괄 등록 요청 DTO
     */
    @Getter
    @NoArgsConstructor
    public static class RegisterRequest {
        private LocalDate prescriptionDate;
        private String patientName;
        private String hospitalName;
        private String diagnosis;
        private Integer durationDays;
        private String notes;

        @Valid
        @NotEmpty(message = "약물 목록은 최소 1개 이상 필요합니다")
        private List<MedicationInfo> medications;
    }

    /**
     * 약물 정보 (처방전 일괄 등록용)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MedicationInfo {
        private String drugItemSeq;      // 식약처 API 약물 코드
        private String customDrugName;   // 직접 입력한 약 이름

        @NotNull(message = "복용량은 필수입니다")
        @Positive(message = "복용량은 양수여야 합니다")
        private Integer dosage;

        @NotNull(message = "복용 횟수는 필수입니다")
        @Positive(message = "복용 횟수는 양수여야 합니다")
        private Integer frequency;

        @Valid
        @NotEmpty(message = "복용 시점은 최소 1개 이상 필요합니다")
        private List<TimingWithTime> timings;

        @NotNull(message = "처방 일수는 필수입니다")
        @Positive(message = "처방 일수는 양수여야 합니다")
        private Integer durationDays;

        @NotNull(message = "총 개수는 필수입니다")
        @Positive(message = "총 개수는 양수여야 합니다")
        private Integer totalCount;

        @NotNull(message = "시작일은 필수입니다")
        private LocalDate startDate;

        private String memo;
    }

    /**
     * 복용 시점 + 알림 시간 쌍
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimingWithTime {
        @NotNull(message = "복용 시점은 필수입니다")
        private MedicationTiming timing;

        @NotNull(message = "알림 시간은 필수입니다")
        private LocalTime time;
    }
}

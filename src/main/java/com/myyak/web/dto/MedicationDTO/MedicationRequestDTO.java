package com.myyak.web.dto.MedicationDTO;

import com.myyak.domain.enums.MedicationTiming;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

public class MedicationRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class CreateRequest {
        // 약물 정보 (둘 중 하나만 사용)
        private String drugItemSeq;      // 식약처 API 약물 코드 (우선)
        private String customDrugName;   // 직접 입력한 약 이름 (API에 없는 경우)

        @NotNull(message = "복용량은 필수입니다")
        @Positive(message = "복용량은 양수여야 합니다")
        private Integer dosage;

        @NotNull(message = "복용 횟수는 필수입니다")
        @Positive(message = "복용 횟수는 양수여야 합니다")
        private Integer frequency;

        @NotEmpty(message = "복용 시점은 최소 1개 이상 필요합니다")
        private List<MedicationTiming> timings;

        @NotNull(message = "처방 일수는 필수입니다")
        @Positive(message = "처방 일수는 양수여야 합니다")
        private Integer durationDays;

        @NotNull(message = "총 개수는 필수입니다")
        @Positive(message = "총 개수는 양수여야 합니다")
        private Integer totalCount;

        @NotNull(message = "시작일은 필수입니다")
        private LocalDate startDate;

        // 선택 필드
        private String memo;  // 사용자 메모
    }

    @Getter
    @NoArgsConstructor
    public static class UpdateRequest {
        private Integer dosage;
        private Integer frequency;
        private List<MedicationTiming> timings;
        private Integer totalCount;
        private Integer remainingCount;
        private String memo;
    }
}

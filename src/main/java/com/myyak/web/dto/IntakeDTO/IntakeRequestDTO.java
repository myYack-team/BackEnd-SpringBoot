package com.myyak.web.dto.IntakeDTO;

import com.myyak.domain.enums.MedicationTiming;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class IntakeRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class CreateRequest {
        @NotEmpty(message = "약 ID 목록은 필수입니다")
        private List<Long> medicationIds;

        @NotNull(message = "복용 시간은 필수입니다")
        private LocalDateTime takenAt;

        @NotNull(message = "복용 시점은 필수입니다")
        private MedicationTiming timing;
    }
}

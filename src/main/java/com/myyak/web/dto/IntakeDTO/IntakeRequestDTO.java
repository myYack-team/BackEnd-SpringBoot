package com.myyak.web.dto.IntakeDTO;

import com.myyak.domain.enums.IntakeStatus;
import com.myyak.domain.enums.MedicationTiming;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class IntakeRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class CreateRequest {
        @NotEmpty(message = "약 ID 목록은 필수입니다")
        @Size(max = 100, message = "한 번에 최대 100개 약을 기록할 수 있습니다")
        private List<@NotNull @Positive Long> medicationIds;

        @NotNull(message = "복용 시간은 필수입니다")
        private LocalDateTime takenAt;

        @NotNull(message = "복용 시점은 필수입니다")
        private MedicationTiming timing;

        // 복용 상태 (기본값: TAKEN)
        private IntakeStatus status = IntakeStatus.TAKEN;
    }
}

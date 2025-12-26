package com.myyak.web.dto.PrescriptionDTO;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

public class PrescriptionRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class UpdateRequest {
        private LocalDate prescriptionDate;
        private String hospitalName;
        private String notes;
    }

    @Getter
    @NoArgsConstructor
    public static class BatchDeleteRequest {
        @NotEmpty(message = "삭제할 ID 목록은 필수입니다")
        private List<Long> ids;
    }
}

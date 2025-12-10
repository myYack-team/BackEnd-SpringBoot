package com.myyak.web.dto.PrescriptionDTO;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

public class PrescriptionRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class UpdateRequest {
        private LocalDate prescriptionDate;
        private String hospitalName;
        private String notes;
    }
}

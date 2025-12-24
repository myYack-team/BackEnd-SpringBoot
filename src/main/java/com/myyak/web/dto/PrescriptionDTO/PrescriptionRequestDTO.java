package com.myyak.web.dto.PrescriptionDTO;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

public class PrescriptionRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class UpdateRequest {
        private LocalDate prescriptionDate;
        private String patientName;      // 환자명
        private String hospitalName;
        private String diagnosis;        // 진단명
        private Integer durationDays;    // 복용 기간 (일)
        private String notes;
    }
}

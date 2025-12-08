package com.myyak.web.dto.ScanDTO;

import com.myyak.domain.enums.MedicationTiming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class ScanResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScannedMedication {
        private String name;             // 처방전에서 추출한 약 이름
        private String drugItemSeq;      // 매칭된 DrugInfo의 itemSeq (없으면 null)
        private Integer dosage;
        private Integer frequency;
        private List<MedicationTiming> timings;
        private Integer durationDays;
        private Integer totalCount;

        // DrugInfo에서 가져온 정보 (매칭된 경우)
        private String efficacy;         // 효능/효과
        private String imageUrl;         // 약 이미지
        private String entpName;         // 제약회사
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScanResult {
        private Boolean success;
        private String confidence;       // high, medium, low
        private List<ScannedMedication> medications;
        private String notes;            // 추가 안내 메시지
    }
}

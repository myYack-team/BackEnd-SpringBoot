package com.myyak.web.dto.DrugApiDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class DrugApiResponseDTO {

    /**
     * DB 통계 응답
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatsResult {
        private long totalCount;  // 저장된 약물 정보 총 건수
    }
}

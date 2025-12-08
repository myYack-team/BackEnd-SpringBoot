package com.myyak.web.dto.DrugInfoDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class DrugInfoResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DrugInfoDetail {
        private String itemSeq;      // 품목기준코드
        private String itemName;     // 제품명
        private String entpName;     // 업체명
        private String efficacy;     // 효능/효과
        private String useMethod;    // 용법/용량
        private String warning;      // 주의사항 경고
        private String caution;      // 주의사항
        private String interaction;  // 상호작용
        private String sideEffect;   // 부작용
        private String storageMethod;// 보관법
        private String imageUrl;     // 약 이미지
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DrugInfoSummary {
        private String itemSeq;      // 품목기준코드
        private String itemName;     // 제품명
        private String entpName;     // 업체명
        private String efficacy;     // 효능 (요약)
        private String imageUrl;     // 약 이미지
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DrugSearchResult {
        private List<DrugInfoSummary> drugs;
        private Integer totalCount;
    }
}

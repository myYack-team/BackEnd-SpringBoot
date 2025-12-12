package com.myyak.web.dto.DrugInfoDTO;

import com.myyak.domain.enums.DrugType;
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
        private String itemName;     // 제품명 (원본)
        private String displayName;  // 표시용 약물명 ("메드론정4밀리그람")
        private String ingredientKr; // 한글 성분명 ("메틸프레드니솔론")
        private String entpName;     // 업체명
        private DrugType drugType;   // 전문/일반/영양제 구분
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
        private String itemName;     // 제품명 (원본)
        private String displayName;  // 표시용 약물명
        private String ingredientKr; // 한글 성분명
        private String entpName;     // 업체명
        private DrugType drugType;   // 전문/일반/영양제 구분
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

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DrugSearchPageResult {
        private List<DrugInfoSummary> drugs;
        private Integer totalCount;
        private Integer page;
        private Integer size;
        private Integer totalPages;
        private Boolean hasNext;
    }
}

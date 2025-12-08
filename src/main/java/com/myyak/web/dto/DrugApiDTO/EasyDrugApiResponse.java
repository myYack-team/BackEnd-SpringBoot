package com.myyak.web.dto.DrugApiDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * e약은요 API 응답 DTO
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EasyDrugApiResponse {

    private Header header;
    private Body body;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Header {
        private String resultCode;
        private String resultMsg;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        private Integer pageNo;
        private Integer totalCount;
        private Integer numOfRows;
        private List<EasyDrugItem> items;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EasyDrugItem {
        private String entpName;        // 업체명
        private String itemName;        // 제품명
        private String itemSeq;         // 품목기준코드
        private String efcyQesitm;      // 효능/효과
        private String useMethodQesitm; // 용법/용량
        private String atpnWarnQesitm;  // 주의사항 경고
        private String atpnQesitm;      // 주의사항
        private String intrcQesitm;     // 상호작용
        private String seQesitm;        // 부작용
        private String depositMethodQesitm; // 보관법
        private String openDe;          // 공개일자
        private String updateDe;        // 수정일자
        private String itemImage;       // 이미지 URL
    }
}

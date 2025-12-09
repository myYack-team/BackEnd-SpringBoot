package com.myyak.web.dto.DrugApiDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 의약품 허가정보 API 응답 DTO
 * API: DrugPrdtPrmsnInfoService07
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DrugPermitApiResponse {

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
        private List<DrugPermitItem> items;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DrugPermitItem {
        @JsonProperty("ITEM_SEQ")
        private String itemSeq;             // 품목기준코드

        @JsonProperty("ITEM_NAME")
        private String itemName;            // 제품명

        @JsonProperty("ENTP_NAME")
        private String entpName;            // 업체명

        @JsonProperty("ITEM_PERMIT_DATE")
        private String itemPermitDate;      // 허가일자

        @JsonProperty("CNSGN_MANUF")
        private String cnsgnManuf;          // 위탁제조업체

        @JsonProperty("ETC_OTC_CODE")
        private String etcOtcCode;          // 전문/일반 구분코드

        @JsonProperty("CHART")
        private String chart;               // 성상

        @JsonProperty("BAR_CODE")
        private String barCode;             // 표준코드

        @JsonProperty("MATERIAL_NAME")
        private String materialName;        // 원료성분

        @JsonProperty("EE_DOC_ID")
        private String eeDocId;             // 효능효과 문서ID

        @JsonProperty("UD_DOC_ID")
        private String udDocId;             // 용법용량 문서ID

        @JsonProperty("NB_DOC_ID")
        private String nbDocId;             // 주의사항 문서ID

        @JsonProperty("INSERT_FILE")
        private String insertFile;          // 첨부파일

        @JsonProperty("STORAGE_METHOD")
        private String storageMethod;       // 저장방법

        @JsonProperty("VALID_TERM")
        private String validTerm;           // 유효기간

        @JsonProperty("REEXAM_TARGET")
        private String reexamTarget;        // 재심사대상

        @JsonProperty("REEXAM_DATE")
        private String reexamDate;          // 재심사기한

        @JsonProperty("PACK_UNIT")
        private String packUnit;            // 포장단위

        @JsonProperty("EDI_CODE")
        private String ediCode;             // 보험코드

        @JsonProperty("DOC_TEXT")
        private String docText;             // 제품설명서

        @JsonProperty("PERMIT_KIND_NAME")
        private String permitKindName;      // 허가종류

        @JsonProperty("ENTP_NO")
        private String entpNo;              // 업체번호

        @JsonProperty("MAKE_MATERIAL_FLAG")
        private String makeMaterialFlag;    // 완제/원료 구분

        @JsonProperty("NARCOTIC_KIND_CODE")
        private String narcoticKindCode;    // 마약류구분코드

        @JsonProperty("NEWDRUG_CLASS_NAME")
        private String newdrugClassName;    // 신약구분명

        @JsonProperty("INDUTY_TYPE")
        private String indutyType;          // 업종구분

        @JsonProperty("CANCEL_DATE")
        private String cancelDate;          // 취소일자

        @JsonProperty("CANCEL_NAME")
        private String cancelName;          // 취소/중지구분

        @JsonProperty("CHANGE_DATE")
        private String changeDate;          // 변경일자

        @JsonProperty("GBN_NAME")
        private String gbnName;             // 등록구분명

        @JsonProperty("TOTAL_CONTENT")
        private String totalContent;        // 총량

        @JsonProperty("EE_DOC_DATA")
        private String eeDocData;           // 효능효과

        @JsonProperty("UD_DOC_DATA")
        private String udDocData;           // 용법용량

        @JsonProperty("NB_DOC_DATA")
        private String nbDocData;           // 주의사항

        @JsonProperty("PN_DOC_DATA")
        private String pnDocData;           // 포장/용기정보

        @JsonProperty("MAIN_ITEM_INGR")
        private String mainItemIngr;        // 주성분코드

        @JsonProperty("INGR_NAME")
        private String ingrName;            // 첨가제

        @JsonProperty("ITEM_INGR_NAME")
        private String itemIngrName;        // 주성분명

        @JsonProperty("ITEM_INGR_CNT")
        private String itemIngrCnt;         // 주성분수

        @JsonProperty("BIG_PRDT_IMG_URL")
        private String bigPrdtImgUrl;       // 제품 큰 이미지

        @JsonProperty("SPCLTY_PBLC")
        private String spcltyPblc;          // 전문/일반 구분명

        @JsonProperty("PRDUCT_TYPE")
        private String prductType;          // 제품유형

        @JsonProperty("PRDUCT_PRMISN_NO")
        private String prductPrmisnNo;      // 제품허가번호

        @JsonProperty("ITEM_ENG_NAME")
        private String itemEngName;         // 제품영문명

        @JsonProperty("ENTP_ENG_NAME")
        private String entpEngName;         // 업체영문명
    }
}

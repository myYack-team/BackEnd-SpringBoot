package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import com.myyak.domain.enums.DrugType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 약물 마스터 정보 테이블
 * 식약처 e약은요 API + 의약품 허가정보 API 데이터 기반
 */
@Entity
@Table(name = "drug_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DrugInfo extends BaseEntity {

    @Id
    private String itemSeq;  // 품목기준코드 (API PK)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String itemName;  // 제품명 (원본: "메드론정4밀리그람(메틸프레드니솔론)")

    @Column(columnDefinition = "TEXT")
    private String displayName;  // 표시용 약물명 (파싱: "메드론정4밀리그람")

    @Column(columnDefinition = "TEXT")
    private String ingredientKr;  // 한글 성분명 (파싱: "메틸프레드니솔론")

    @Column(columnDefinition = "TEXT")
    private String entpName;  // 업체명 (제약회사)

    @Column(columnDefinition = "TEXT")
    private String efficacy;  // efcyQesitm - 효능/효과

    @Column(columnDefinition = "TEXT")
    private String useMethod;  // useMethodQesitm - 용법/용량

    @Column(columnDefinition = "TEXT")
    private String warning;  // atpnWarnQesitm - 주의사항 경고

    @Column(columnDefinition = "TEXT")
    private String caution;  // atpnQesitm - 주의사항

    @Column(columnDefinition = "TEXT")
    private String interaction;  // intrcQesitm - 상호작용

    @Column(columnDefinition = "TEXT")
    private String sideEffect;  // seQesitm - 부작용

    @Column(columnDefinition = "TEXT")
    private String storageMethod;  // depositMethodQesitm - 보관법

    @Column(length = 500)
    private String imageUrl;  // itemImage - 약 이미지 URL

    private LocalDate openDate;  // 공개일자

    private LocalDate apiUpdateDate;  // API 수정일자

    // 의약품 허가정보 API 추가 필드
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DrugType drugType;  // 전문/일반 구분

    @Column(columnDefinition = "TEXT")
    private String ingredientName;  // 주성분명

    private LocalDate permitDate;  // 허가일자

    public void updateFromApi(String itemName, String displayName, String ingredientKr,
                               String entpName, String efficacy,
                               String useMethod, String warning, String caution,
                               String interaction, String sideEffect, String storageMethod,
                               String imageUrl, LocalDate openDate, LocalDate apiUpdateDate) {
        this.itemName = itemName;
        this.displayName = displayName;
        this.ingredientKr = ingredientKr;
        this.entpName = entpName;
        this.efficacy = efficacy;
        this.useMethod = useMethod;
        this.warning = warning;
        this.caution = caution;
        this.interaction = interaction;
        this.sideEffect = sideEffect;
        this.storageMethod = storageMethod;
        this.imageUrl = imageUrl;
        this.openDate = openDate;
        this.apiUpdateDate = apiUpdateDate;
    }

    public void updateFromPermitApi(String itemName, String displayName, String ingredientKr,
                                      String entpName, DrugType drugType,
                                      String ingredientName, String efficacy, String useMethod,
                                      String caution, String storageMethod, String imageUrl, LocalDate permitDate) {
        this.itemName = itemName;
        this.displayName = displayName;
        this.ingredientKr = ingredientKr;
        this.entpName = entpName;
        this.drugType = drugType;
        this.ingredientName = ingredientName;
        // efficacy 등은 기존 값이 없을 때만 업데이트 (e약은요 API 데이터 우선)
        if ((this.efficacy == null || this.efficacy.isBlank()) && efficacy != null) {
            this.efficacy = efficacy;
        }
        if ((this.useMethod == null || this.useMethod.isBlank()) && useMethod != null) {
            this.useMethod = useMethod;
        }
        if ((this.caution == null || this.caution.isBlank()) && caution != null) {
            this.caution = caution;
        }
        if ((this.storageMethod == null || this.storageMethod.isBlank()) && storageMethod != null) {
            this.storageMethod = storageMethod;
        }
        if (imageUrl != null && !imageUrl.isBlank()) {
            this.imageUrl = imageUrl;
        }
        this.permitDate = permitDate;
    }

    /**
     * 크롤링으로 효능 정보만 업데이트
     */
    public void updateEfficacy(String efficacy) {
        if (efficacy != null && !efficacy.isBlank()) {
            this.efficacy = efficacy;
        }
    }
}

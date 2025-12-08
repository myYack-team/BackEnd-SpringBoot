package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 약물 마스터 정보 테이블
 * 식약처 e약은요 API 데이터 기반
 */
@Entity
@Table(name = "drug_info", indexes = {
    @Index(name = "idx_drug_item_name", columnList = "itemName"),
    @Index(name = "idx_drug_entp_name", columnList = "entpName")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DrugInfo extends BaseEntity {

    @Id
    private String itemSeq;  // 품목기준코드 (API PK)

    @Column(nullable = false, length = 500)
    private String itemName;  // 제품명

    @Column(length = 300)
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

    public void updateFromApi(String itemName, String entpName, String efficacy,
                               String useMethod, String warning, String caution,
                               String interaction, String sideEffect, String storageMethod,
                               String imageUrl, LocalDate openDate, LocalDate apiUpdateDate) {
        this.itemName = itemName;
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
}

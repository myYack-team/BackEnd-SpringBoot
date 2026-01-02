package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import com.myyak.domain.enums.DrugType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 테스트용 약물 마스터 정보 테이블
 * DrugInfo와 동일한 구조로 테스트 모드 배치 수집에 사용
 * 검증 후 삭제됨
 */
@Entity
@Table(name = "drug_info_test")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DrugInfoTest extends BaseEntity {

    @Id
    private String itemSeq;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String itemName;

    @Column(columnDefinition = "TEXT")
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String ingredientKr;

    @Column(columnDefinition = "TEXT")
    private String entpName;

    @Column(columnDefinition = "TEXT")
    private String efficacy;

    @Column(name = "`usage`", columnDefinition = "TEXT")
    private String usage;

    @Column(columnDefinition = "TEXT")
    private String precaution;

    @Column(length = 500)
    private String productImage;

    @Column(columnDefinition = "TEXT")
    private String warning;

    @Column(columnDefinition = "TEXT")
    private String caution;

    @Column(columnDefinition = "TEXT")
    private String interaction;

    @Column(columnDefinition = "TEXT")
    private String sideEffect;

    @Column(columnDefinition = "TEXT")
    private String storageMethod;

    @Column(length = 500)
    private String imageUrl;

    private LocalDate openDate;

    private LocalDate apiUpdateDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DrugType drugType;

    @Column(columnDefinition = "TEXT")
    private String ingredientName;

    private LocalDate permitDate;

    /**
     * DrugInfo 엔티티로부터 복사 생성
     */
    public static DrugInfoTest from(DrugInfo drugInfo) {
        return DrugInfoTest.builder()
                .itemSeq(drugInfo.getItemSeq())
                .itemName(drugInfo.getItemName())
                .displayName(drugInfo.getDisplayName())
                .ingredientKr(drugInfo.getIngredientKr())
                .entpName(drugInfo.getEntpName())
                .efficacy(drugInfo.getEfficacy())
                .usage(drugInfo.getUsage())
                .precaution(drugInfo.getPrecaution())
                .productImage(drugInfo.getProductImage())
                .warning(drugInfo.getWarning())
                .caution(drugInfo.getCaution())
                .interaction(drugInfo.getInteraction())
                .sideEffect(drugInfo.getSideEffect())
                .storageMethod(drugInfo.getStorageMethod())
                .imageUrl(drugInfo.getImageUrl())
                .openDate(drugInfo.getOpenDate())
                .apiUpdateDate(drugInfo.getApiUpdateDate())
                .drugType(drugInfo.getDrugType())
                .ingredientName(drugInfo.getIngredientName())
                .permitDate(drugInfo.getPermitDate())
                .build();
    }

    public void updateFromApi(String itemName, String displayName, String ingredientKr,
                               String entpName, String efficacy,
                               String usage, String warning, String caution,
                               String interaction, String sideEffect, String storageMethod,
                               String imageUrl, LocalDate openDate, LocalDate apiUpdateDate) {
        this.itemName = itemName;
        this.displayName = displayName;
        this.ingredientKr = ingredientKr;
        this.entpName = entpName;
        this.efficacy = efficacy;
        this.usage = usage;
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
                                      String ingredientName, String efficacy, String usage,
                                      String caution, String storageMethod, String imageUrl, LocalDate permitDate) {
        this.itemName = itemName;
        this.displayName = displayName;
        this.ingredientKr = ingredientKr;
        this.entpName = entpName;
        this.drugType = drugType;
        this.ingredientName = ingredientName;
        if ((this.efficacy == null || this.efficacy.isBlank()) && efficacy != null) {
            this.efficacy = efficacy;
        }
        if ((this.usage == null || this.usage.isBlank()) && usage != null) {
            this.usage = usage;
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

    public void updateParsedNames(String displayName, String ingredientKr) {
        if (displayName != null && !displayName.isBlank()) {
            this.displayName = displayName;
        }
        if (ingredientKr != null && !ingredientKr.isBlank()) {
            this.ingredientKr = ingredientKr;
        }
    }
}

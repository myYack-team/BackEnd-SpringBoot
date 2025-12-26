package com.myyak.repository.projection;

/**
 * DrugInfo 경량 조회용 Projection 인터페이스
 * TEXT 컬럼(효능, 용법 등)을 제외한 목록 표시용 필드만 조회
 */
public interface DrugInfoSummary {

    String getItemSeq();

    String getItemName();

    String getDisplayName();

    String getIngredientKr();

    String getEntpName();

    String getImageUrl();

    String getDrugType();
}


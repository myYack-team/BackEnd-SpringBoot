package com.myyak.repository.projection;

import com.myyak.domain.enums.DrugType;

/**
 * DrugInfo 목록 조회용 경량 Projection
 * TEXT 컬럼(efficacy, usage, precaution 등)을 제외하여 조회 성능 개선
 */
public interface DrugInfoSummary {
    String getItemSeq();
    String getItemName();
    String getDisplayName();
    String getIngredientKr();
    String getEntpName();
    String getImageUrl();
    DrugType getDrugType();
}

package com.myyak.service.drugSearchService;

import com.myyak.domain.enums.DrugType;
import lombok.Getter;

/**
 * 약물 검색용 경량 캐시 DTO
 *
 * DrugInfo 엔티티 전체(효능, 부작용 등 긴 텍스트 포함)를 캐싱하면
 * 메모리 부족(OOM)이 발생하므로, 검색에 필요한 최소 필드만 캐싱합니다.
 *
 * 예상 메모리 사용량:
 * - DrugInfo 전체: 약물 1건당 5~20KB → 10만 건 시 500MB~2GB
 * - DrugSearchCache: 약물 1건당 200~500bytes → 10만 건 시 20~50MB
 *
 * [검색 성능 최적화]
 * 검색 시마다 전 항목에 대해 공백 제거/소문자 변환을 반복하지 않도록,
 * 캐시 적재 시점에 정규화된 이름(normalizedItemName, normalizedDisplayName)을 미리 계산합니다.
 */
@Getter
public class DrugSearchCache {
    private final String itemSeq;       // 품목기준코드 (PK)
    private final String itemName;      // 제품명 (검색용)
    private final String displayName;   // 표시용 약물명 (검색용)
    private final DrugType drugType;    // 전문/일반 구분 (목록 표시용)
    private final String imageUrl;      // 이미지 URL (목록 표시용)

    private final String normalizedItemName;     // 공백 제거 + 소문자 변환된 제품명 (검색 비교용)
    private final String normalizedDisplayName;  // 공백 제거 + 소문자 변환된 표시명 (검색 비교용)

    public DrugSearchCache(String itemSeq, String itemName, String displayName,
                           DrugType drugType, String imageUrl) {
        this.itemSeq = itemSeq;
        this.itemName = itemName;
        this.displayName = displayName;
        this.drugType = drugType;
        this.imageUrl = imageUrl;
        this.normalizedItemName = normalize(itemName);
        this.normalizedDisplayName = normalize(displayName);
    }

    /**
     * 검색 비교용 정규화 (공백 제거 + 소문자 변환)
     */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("\\s+", "").toLowerCase();
    }
}

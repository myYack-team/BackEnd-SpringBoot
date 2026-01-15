package com.myyak.service.drugSearchService;

import com.myyak.domain.enums.DrugType;
import lombok.AllArgsConstructor;
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
 */
@Getter
@AllArgsConstructor
public class DrugSearchCache {
    private final String itemSeq;       // 품목기준코드 (PK)
    private final String itemName;      // 제품명 (검색용)
    private final String displayName;   // 표시용 약물명 (검색용)
    private final DrugType drugType;    // 전문/일반 구분 (목록 표시용)
    private final String imageUrl;      // 이미지 URL (목록 표시용)
}

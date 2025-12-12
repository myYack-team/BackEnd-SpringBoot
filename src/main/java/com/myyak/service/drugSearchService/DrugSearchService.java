package com.myyak.service.drugSearchService;

import com.myyak.domain.DrugInfo;

import java.util.Optional;

/**
 * 자모 기반 편집거리 검색 서비스
 * OCR 오타 처리를 위한 한글 자모 분해 + Levenshtein Distance 기반 약물 검색
 */
public interface DrugSearchService {

    /**
     * 편집거리 기반 약물 검색
     * @param drugName OCR로 인식된 약물명 (오타 포함 가능)
     * @return 가장 유사한 약물 (편집거리 임계값 이내)
     */
    Optional<DrugInfo> findByEditDistance(String drugName);

    /**
     * 편집거리 기반 약물 검색 (임계값 지정)
     * @param drugName OCR로 인식된 약물명
     * @param threshold 편집거리 임계값
     * @return 가장 유사한 약물
     */
    Optional<DrugInfo> findByEditDistance(String drugName, int threshold);

    /**
     * 캐시 초기화 (약물 DB 업데이트 후 호출)
     */
    void refreshCache();

    /**
     * 캐시된 약물 수 조회
     * @return 캐시된 약물 수
     */
    int getCacheSize();
}
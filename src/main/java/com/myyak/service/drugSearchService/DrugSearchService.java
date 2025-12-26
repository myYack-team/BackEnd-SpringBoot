package com.myyak.service.drugSearchService;

import com.myyak.domain.DrugInfo;

import java.util.List;
import java.util.Optional;

/**
 * 약물 검색 서비스
 * - 메모리 캐시 기반 빠른 검색 (DB 쿼리 없음)
 * - 자모 분해 + Levenshtein Distance 기반 오타 교정
 */
public interface DrugSearchService {

    /**
     * 메모리 캐시에서 약물명 부분 일치 검색 (DB 쿼리 없음, 매우 빠름)
     * @param keyword 검색 키워드
     * @return 매칭된 약물 (없으면 empty)
     */
    Optional<DrugInfo> findByNameContaining(String keyword);

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

    /**
     * 메모리 캐시에서 약물 검색 (페이징, 관련도 정렬)
     * - prefix 매칭 우선, 그 다음 contains 매칭
     * - DB 쿼리 없이 ~1ms 응답
     *
     * @param keyword 검색 키워드
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기
     * @return 검색 결과 리스트
     */
    List<DrugInfo> searchFromCache(String keyword, int page, int size);

    /**
     * 메모리 캐시에서 약물 검색 결과 총 개수
     * @param keyword 검색 키워드
     * @return 매칭된 총 약물 수
     */
    long countFromCache(String keyword);
}
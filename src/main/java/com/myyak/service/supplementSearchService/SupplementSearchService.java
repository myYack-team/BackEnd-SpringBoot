package com.myyak.service.supplementSearchService;

import com.myyak.domain.Supplement;
import com.myyak.domain.enums.SupplementTag;

import java.util.List;

/**
 * 영양제 캐시 기반 검색 서비스
 */
public interface SupplementSearchService {

    /**
     * 캐시에서 영양제 검색 (페이징)
     * @param keyword 검색 키워드
     * @param tag 태그 필터 (nullable)
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기
     * @return 검색 결과 리스트
     */
    List<Supplement> searchFromCache(String keyword, SupplementTag tag, int page, int size);

    /**
     * 캐시에서 검색 결과 개수 조회
     * @param keyword 검색 키워드
     * @param tag 태그 필터 (nullable)
     * @return 매칭된 총 개수
     */
    long countFromCache(String keyword, SupplementTag tag);

    /**
     * 인기 영양제 조회 (캐시)
     * @param page 페이지 번호
     * @param size 페이지 크기
     * @return 인기순 정렬된 영양제 리스트
     */
    List<Supplement> getPopularFromCache(int page, int size);

    /**
     * 인기 영양제 총 개수
     * @return 총 영양제 개수
     */
    long countAll();

    /**
     * 캐시 갱신 (데이터 변경 시 호출)
     */
    void refreshCache();

    /**
     * 특정 영양제 캐시 추가/갱신
     */
    void addOrUpdateCache(Supplement supplement);

    /**
     * 특정 영양제 캐시 제거
     */
    void removeFromCache(Long supplementId);

    /**
     * 캐시 크기 조회
     */
    int getCacheSize();
}

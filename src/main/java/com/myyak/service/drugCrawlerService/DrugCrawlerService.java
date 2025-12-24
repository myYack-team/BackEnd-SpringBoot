package com.myyak.service.drugCrawlerService;

/**
 * 의약품안전나라 크롤링 서비스 인터페이스
 * 효능 정보가 없는 의약품에 대해 크롤링으로 데이터 보완
 */
public interface DrugCrawlerService {

    /**
     * 효능 정보가 없는 모든 약물에 대해 크롤링 수행
     * @return 업데이트된 약물 수
     */
    int crawlEfficacyForMissingDrugs();

    /**
     * 특정 품목기준코드의 약물 효능 정보 크롤링
     * @param itemSeq 품목기준코드
     * @return 크롤링 성공 여부
     */
    boolean crawlEfficacyByItemSeq(String itemSeq);

    /**
     * 크롤링 대상 약물 수 조회 (효능 없는 약물 수)
     * @return 크롤링 대상 수
     */
    long countDrugsWithoutEfficacy();
}

package com.myyak.service.embeddingService;

import com.myyak.web.dto.EmbeddingDTO.EmbeddingResponseDTO;

public interface EmbeddingService {

    /**
     * 유사 약물 검색
     * @param query 검색 쿼리 (약물명, 오타 포함 가능)
     * @param topK 반환할 최대 결과 수
     * @return 유사도 순으로 정렬된 약물 목록
     */
    EmbeddingResponseDTO.SimilarDrugSearchResult searchSimilarDrugs(String query, int topK);

    /**
     * 단일 약물 임베딩 생성
     * @param itemSeq 품목기준코드
     * @return 생성 결과
     */
    EmbeddingResponseDTO.EmbeddingCreateResult createEmbedding(String itemSeq);

    /**
     * 전체 약물 배치 임베딩 생성
     * @param batchSize 배치 크기
     * @return 배치 처리 결과
     */
    EmbeddingResponseDTO.BatchEmbeddingResult createBatchEmbeddings(int batchSize);

    /**
     * 임베딩 통계 조회
     * @return 임베딩 생성 현황 통계
     */
    EmbeddingResponseDTO.EmbeddingStats getEmbeddingStats();

    /**
     * 임베딩 존재 여부 확인
     * @param itemSeq 품목기준코드
     * @return 존재 여부
     */
    boolean hasEmbedding(String itemSeq);

    /**
     * 전체 약물 임베딩 생성 (비동기)
     * 백그라운드에서 모든 약물의 임베딩을 순차적으로 생성
     */
    void createAllEmbeddingsAsync();
}

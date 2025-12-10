package com.myyak.web.dto.EmbeddingDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class EmbeddingResponseDTO {

    /**
     * 유사 약물 검색 결과 (단일 항목)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarDrug {
        private String itemSeq;      // 품목기준코드
        private String itemName;     // 제품명
        private String entpName;     // 업체명
        private String imageUrl;     // 약 이미지
        private Double similarity;   // 유사도 (0.0 ~ 1.0)
    }

    /**
     * 유사 약물 검색 결과 목록
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarDrugSearchResult {
        private String query;                // 검색 쿼리
        private List<SimilarDrug> results;   // 유사 약물 목록
        private Integer totalCount;          // 결과 수
    }

    /**
     * 임베딩 생성 결과 (단일)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbeddingCreateResult {
        private String itemSeq;      // 품목기준코드
        private String itemName;     // 제품명
        private Integer dimension;   // 벡터 차원
        private Boolean success;     // 성공 여부
        private String message;      // 메시지
    }

    /**
     * 배치 임베딩 생성 결과
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchEmbeddingResult {
        private Integer totalRequested;  // 요청된 약물 수
        private Integer successCount;    // 성공 수
        private Integer failCount;       // 실패 수
        private Integer skippedCount;    // 건너뛴 수 (이미 존재)
        private List<String> failedItemSeqs;  // 실패한 품목코드 목록
        private Long elapsedTimeMs;      // 소요 시간 (ms)
    }

    /**
     * 임베딩 통계 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbeddingStats {
        private Long totalDrugs;         // 전체 약물 수
        private Long embeddedCount;      // 임베딩 완료 수
        private Long pendingCount;       // 임베딩 대기 수
        private Double completionRate;   // 완료율 (%)
    }
}

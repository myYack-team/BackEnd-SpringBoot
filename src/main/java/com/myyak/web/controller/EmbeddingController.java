package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.service.embeddingService.EmbeddingService;
import com.myyak.web.dto.EmbeddingDTO.EmbeddingResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Embedding", description = "약물 임베딩 기반 유사도 검색 API")
@RestController
@RequestMapping("/api/drugs/embedding")
@RequiredArgsConstructor
public class EmbeddingController {

    private final EmbeddingService embeddingService;

    @Operation(summary = "유사 약물 검색", description = "입력 텍스트와 유사한 약물을 검색합니다 (오타 허용)")
    @GetMapping("/search")
    public ApiResponse<EmbeddingResponseDTO.SimilarDrugSearchResult> searchSimilarDrugs(
            @Parameter(description = "검색어 (약물명, 오타 포함 가능)") @RequestParam String query,
            @Parameter(description = "반환할 최대 결과 수") @RequestParam(defaultValue = "10") int topK) {

        EmbeddingResponseDTO.SimilarDrugSearchResult result = embeddingService.searchSimilarDrugs(query, topK);
        return ApiResponse.onSuccess(result);
    }

    @Operation(summary = "단일 약물 임베딩 생성", description = "특정 약물의 임베딩 벡터를 생성합니다")
    @PostMapping("/generate/{itemSeq}")
    public ApiResponse<EmbeddingResponseDTO.EmbeddingCreateResult> createEmbedding(
            @Parameter(description = "품목기준코드") @PathVariable String itemSeq) {

        EmbeddingResponseDTO.EmbeddingCreateResult result = embeddingService.createEmbedding(itemSeq);
        return ApiResponse.onSuccess(result);
    }

    @Operation(summary = "배치 임베딩 생성", description = "임베딩이 없는 약물들의 임베딩을 배치로 생성합니다")
    @PostMapping("/generate/batch")
    public ApiResponse<EmbeddingResponseDTO.BatchEmbeddingResult> createBatchEmbeddings(
            @Parameter(description = "배치 크기 (기본값: 100)") @RequestParam(defaultValue = "100") int batchSize) {

        EmbeddingResponseDTO.BatchEmbeddingResult result = embeddingService.createBatchEmbeddings(batchSize);
        return ApiResponse.onSuccess(result);
    }

    @Operation(summary = "임베딩 통계 조회", description = "임베딩 생성 현황 통계를 조회합니다")
    @GetMapping("/stats")
    public ApiResponse<EmbeddingResponseDTO.EmbeddingStats> getEmbeddingStats() {
        EmbeddingResponseDTO.EmbeddingStats stats = embeddingService.getEmbeddingStats();
        return ApiResponse.onSuccess(stats);
    }

    @Operation(summary = "임베딩 존재 여부 확인", description = "특정 약물의 임베딩 존재 여부를 확인합니다")
    @GetMapping("/exists/{itemSeq}")
    public ApiResponse<Boolean> hasEmbedding(
            @Parameter(description = "품목기준코드") @PathVariable String itemSeq) {

        boolean exists = embeddingService.hasEmbedding(itemSeq);
        return ApiResponse.onSuccess(exists);
    }
}

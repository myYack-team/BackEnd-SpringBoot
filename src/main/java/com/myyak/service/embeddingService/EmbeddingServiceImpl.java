package com.myyak.service.embeddingService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.domain.DrugEmbedding;
import com.myyak.domain.DrugInfo;
import com.myyak.repository.DrugEmbeddingRepository;
import com.myyak.repository.DrugInfoRepository;
import com.myyak.web.dto.EmbeddingDTO.EmbeddingResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EmbeddingServiceImpl implements EmbeddingService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final DrugInfoRepository drugInfoRepository;
    private final DrugEmbeddingRepository drugEmbeddingRepository;

    @Value("${ai.openai.api-key:}")
    private String openaiApiKey;

    private static final String OPENAI_EMBEDDING_URL = "https://api.openai.com/v1/embeddings";
    private static final String EMBEDDING_MODEL = "text-embedding-3-small";
    private static final int EMBEDDING_DIMENSION = 1536;

    @Override
    @Transactional(readOnly = true)
    public EmbeddingResponseDTO.SimilarDrugSearchResult searchSimilarDrugs(String query, int topK) {
        if (query == null || query.isBlank()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }

        // 쿼리 임베딩 생성
        float[] queryVector = getEmbeddingFromOpenAI(query);
        if (queryVector == null) {
            return EmbeddingResponseDTO.SimilarDrugSearchResult.builder()
                    .query(query)
                    .results(List.of())
                    .totalCount(0)
                    .build();
        }

        // 전체 임베딩과 유사도 계산
        List<DrugEmbedding> allEmbeddings = drugEmbeddingRepository.findAll();

        List<SimilarDrugWithScore> scoredDrugs = allEmbeddings.stream()
                .map(embedding -> {
                    float[] drugVector = embedding.getEmbeddingVector();
                    double similarity = cosineSimilarity(queryVector, drugVector);
                    return new SimilarDrugWithScore(embedding, similarity);
                })
                .sorted((a, b) -> Double.compare(b.similarity, a.similarity))
                .limit(topK)
                .collect(Collectors.toList());

        // DrugInfo 조회하여 상세 정보 포함
        List<EmbeddingResponseDTO.SimilarDrug> results = scoredDrugs.stream()
                .map(scored -> {
                    DrugInfo drugInfo = drugInfoRepository.findById(scored.embedding.getItemSeq())
                            .orElse(null);

                    return EmbeddingResponseDTO.SimilarDrug.builder()
                            .itemSeq(scored.embedding.getItemSeq())
                            .itemName(scored.embedding.getItemName())
                            .entpName(drugInfo != null ? drugInfo.getEntpName() : null)
                            .imageUrl(drugInfo != null ? drugInfo.getImageUrl() : null)
                            .similarity(Math.round(scored.similarity * 10000) / 10000.0)
                            .build();
                })
                .collect(Collectors.toList());

        return EmbeddingResponseDTO.SimilarDrugSearchResult.builder()
                .query(query)
                .results(results)
                .totalCount(results.size())
                .build();
    }

    @Override
    public EmbeddingResponseDTO.EmbeddingCreateResult createEmbedding(String itemSeq) {
        // 이미 존재하는지 확인
        if (drugEmbeddingRepository.existsByItemSeq(itemSeq)) {
            return EmbeddingResponseDTO.EmbeddingCreateResult.builder()
                    .itemSeq(itemSeq)
                    .success(false)
                    .message("이미 임베딩이 존재합니다.")
                    .build();
        }

        // DrugInfo 조회
        DrugInfo drugInfo = drugInfoRepository.findById(itemSeq)
                .orElseThrow(() -> new GeneralException(ErrorStatus._NOT_FOUND));

        // 임베딩 생성
        float[] vector = getEmbeddingFromOpenAI(drugInfo.getItemName());
        if (vector == null) {
            return EmbeddingResponseDTO.EmbeddingCreateResult.builder()
                    .itemSeq(itemSeq)
                    .itemName(drugInfo.getItemName())
                    .success(false)
                    .message("임베딩 생성에 실패했습니다.")
                    .build();
        }

        // 저장
        DrugEmbedding embedding = DrugEmbedding.create(itemSeq, drugInfo.getItemName(), vector);
        drugEmbeddingRepository.save(embedding);

        return EmbeddingResponseDTO.EmbeddingCreateResult.builder()
                .itemSeq(itemSeq)
                .itemName(drugInfo.getItemName())
                .dimension(EMBEDDING_DIMENSION)
                .success(true)
                .message("임베딩이 생성되었습니다.")
                .build();
    }

    @Override
    public EmbeddingResponseDTO.BatchEmbeddingResult createBatchEmbeddings(int batchSize) {
        long startTime = System.currentTimeMillis();

        // 임베딩이 없는 약물 조회
        List<String> pendingItemSeqs = drugEmbeddingRepository.findItemSeqsWithoutEmbedding();

        if (pendingItemSeqs.isEmpty()) {
            return EmbeddingResponseDTO.BatchEmbeddingResult.builder()
                    .totalRequested(0)
                    .successCount(0)
                    .failCount(0)
                    .skippedCount(0)
                    .failedItemSeqs(List.of())
                    .elapsedTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        // batchSize만큼 처리
        List<String> targetItemSeqs = pendingItemSeqs.stream()
                .limit(batchSize)
                .collect(Collectors.toList());

        int successCount = 0;
        int failCount = 0;
        List<String> failedItemSeqs = new ArrayList<>();

        for (String itemSeq : targetItemSeqs) {
            try {
                DrugInfo drugInfo = drugInfoRepository.findById(itemSeq).orElse(null);
                if (drugInfo == null) {
                    failCount++;
                    failedItemSeqs.add(itemSeq);
                    continue;
                }

                float[] vector = getEmbeddingFromOpenAI(drugInfo.getItemName());
                if (vector == null) {
                    failCount++;
                    failedItemSeqs.add(itemSeq);
                    continue;
                }

                DrugEmbedding embedding = DrugEmbedding.create(itemSeq, drugInfo.getItemName(), vector);
                drugEmbeddingRepository.save(embedding);
                successCount++;

                // Rate limit 방지 (100ms 딜레이)
                Thread.sleep(100);

            } catch (Exception e) {
                log.error("Failed to create embedding for {}: {}", itemSeq, e.getMessage());
                failCount++;
                failedItemSeqs.add(itemSeq);
            }
        }

        return EmbeddingResponseDTO.BatchEmbeddingResult.builder()
                .totalRequested(targetItemSeqs.size())
                .successCount(successCount)
                .failCount(failCount)
                .skippedCount(0)
                .failedItemSeqs(failedItemSeqs)
                .elapsedTimeMs(System.currentTimeMillis() - startTime)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public EmbeddingResponseDTO.EmbeddingStats getEmbeddingStats() {
        long totalDrugs = drugInfoRepository.count();
        long embeddedCount = drugEmbeddingRepository.count();
        long pendingCount = totalDrugs - embeddedCount;
        double completionRate = totalDrugs > 0
                ? Math.round(embeddedCount * 10000.0 / totalDrugs) / 100.0
                : 0.0;

        return EmbeddingResponseDTO.EmbeddingStats.builder()
                .totalDrugs(totalDrugs)
                .embeddedCount(embeddedCount)
                .pendingCount(pendingCount)
                .completionRate(completionRate)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasEmbedding(String itemSeq) {
        return drugEmbeddingRepository.existsByItemSeq(itemSeq);
    }

    /**
     * OpenAI Embedding API 호출
     */
    private float[] getEmbeddingFromOpenAI(String text) {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            log.warn("OpenAI API key is not configured");
            return null;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", EMBEDDING_MODEL);
            requestBody.put("input", text);

            String response = webClient.post()
                    .uri(OPENAI_EMBEDDING_URL)
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseEmbeddingResponse(response);

        } catch (Exception e) {
            log.error("Failed to get embedding from OpenAI: {}", e.getMessage());
            return null;
        }
    }

    /**
     * OpenAI Embedding 응답 파싱
     */
    private float[] parseEmbeddingResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode dataArray = root.path("data");

            if (dataArray.isEmpty() || !dataArray.isArray()) {
                log.error("Invalid embedding response: no data array");
                return null;
            }

            JsonNode embeddingArray = dataArray.get(0).path("embedding");
            if (!embeddingArray.isArray()) {
                log.error("Invalid embedding response: no embedding array");
                return null;
            }

            float[] vector = new float[embeddingArray.size()];
            for (int i = 0; i < embeddingArray.size(); i++) {
                vector[i] = (float) embeddingArray.get(i).asDouble();
            }

            return vector;

        } catch (Exception e) {
            log.error("Failed to parse embedding response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 코사인 유사도 계산
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0.0 : dotProduct / denominator;
    }

    /**
     * 유사도 스코어와 함께 저장할 내부 클래스
     */
    private static class SimilarDrugWithScore {
        final DrugEmbedding embedding;
        final double similarity;

        SimilarDrugWithScore(DrugEmbedding embedding, double similarity) {
            this.embedding = embedding;
            this.similarity = similarity;
        }
    }

    @Override
    @Async
    @Transactional
    public void createAllEmbeddingsAsync() {
        log.info("=== 전체 임베딩 생성 시작 ===");
        long startTime = System.currentTimeMillis();

        List<String> pendingItemSeqs = drugEmbeddingRepository.findItemSeqsWithoutEmbedding();
        int total = pendingItemSeqs.size();
        int successCount = 0;
        int failCount = 0;

        log.info("생성 대상: {}개", total);

        for (int i = 0; i < pendingItemSeqs.size(); i++) {
            String itemSeq = pendingItemSeqs.get(i);

            try {
                DrugInfo drugInfo = drugInfoRepository.findById(itemSeq).orElse(null);
                if (drugInfo == null) {
                    failCount++;
                    continue;
                }

                float[] vector = getEmbeddingFromOpenAI(drugInfo.getItemName());
                if (vector == null) {
                    failCount++;
                    continue;
                }

                DrugEmbedding embedding = DrugEmbedding.create(itemSeq, drugInfo.getItemName(), vector);
                drugEmbeddingRepository.saveAndFlush(embedding);
                successCount++;

                // 진행률 로그 (100개마다)
                if ((i + 1) % 100 == 0) {
                    double progress = (i + 1) * 100.0 / total;
                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                    log.info("[진행] {}/{} ({}%) - 성공: {}, 실패: {}, 경과: {}초",
                            i + 1, total, String.format("%.1f", progress), successCount, failCount, elapsed);
                }

                // Rate limit 방지
                Thread.sleep(10);

            } catch (Exception e) {
                log.error("임베딩 생성 실패 [{}]: {}", itemSeq, e.getMessage());
                failCount++;
            }
        }

        long totalTime = (System.currentTimeMillis() - startTime) / 1000;
        log.info("=== 전체 임베딩 생성 완료 ===");
        log.info("총 {}개 중 성공: {}, 실패: {}, 소요시간: {}초", total, successCount, failCount, totalTime);
    }
}

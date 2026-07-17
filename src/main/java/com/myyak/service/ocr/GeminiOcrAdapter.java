package com.myyak.service.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * Gemini API를 OCR로 활용하는 어댑터
 * - Google Cloud Vision 대신 Gemini의 이미지 인식 기능 사용
 * - 텍스트 추출만 수행 (구조화는 파서에서 처리)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ocr.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiOcrAdapter implements OcrClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.gemini.model:gemini-3.1-flash-lite}")
    private String geminiModel;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    // 텍스트 추출 전용 프롬프트 (간단하고 빠름)
    private static final String OCR_PROMPT = """
            이 이미지에서 모든 텍스트를 추출하세요.

            규칙:
            1. 이미지에 보이는 모든 텍스트를 그대로 추출
            2. 줄바꿈을 유지
            3. 표나 레이아웃 구조가 있으면 적절히 구분
            4. JSON이나 구조화 없이 순수 텍스트만 출력
            5. 설명이나 해석 없이 텍스트만 출력

            텍스트:
            """;

    @Override
    public OcrResult extractText(byte[] imageBytes) {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            log.warn("Gemini API key가 설정되지 않음");
            return OcrResult.builder()
                    .fullText("")
                    .blocks(List.of())
                    .confidence(0f)
                    .build();
        }

        long startTime = System.currentTimeMillis();

        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            Map<String, Object> requestBody = buildGeminiRequest(base64Image);

            String geminiUrl = GEMINI_API_URL + geminiModel + ":generateContent?key=" + geminiApiKey;

            String response = webClient.post()
                    .uri(geminiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String extractedText = parseGeminiResponse(response);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Gemini OCR 완료: {}ms, 텍스트 길이: {}", elapsed, extractedText.length());

            return OcrResult.builder()
                    .fullText(extractedText)
                    .blocks(List.of()) // Gemini는 위치 정보 제공 안 함
                    .confidence(extractedText.isEmpty() ? 0f : 0.85f)
                    .build();

        } catch (Exception e) {
            log.error("Gemini OCR 실패: ", e);
            return OcrResult.builder()
                    .fullText("")
                    .blocks(List.of())
                    .confidence(0f)
                    .build();
        }
    }

    private Map<String, Object> buildGeminiRequest(String base64Image) {
        Map<String, Object> inlineData = new HashMap<>();
        inlineData.put("mime_type", "image/jpeg");
        inlineData.put("data", base64Image);

        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("inline_data", inlineData);

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", OCR_PROMPT);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(textPart, imagePart));

        Map<String, Object> request = new HashMap<>();
        request.put("contents", List.of(content));

        // 빠른 응답을 위한 설정
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("maxOutputTokens", 4096);
        generationConfig.put("temperature", 0.1); // 낮은 temperature로 정확한 추출
        request.put("generationConfig", generationConfig);

        return request;
    }

    private String parseGeminiResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                log.error("Gemini API 에러: {}", error.path("message").asText());
                return "";
            }

            JsonNode candidates = root.path("candidates");
            if (candidates.isEmpty() || !candidates.isArray() || candidates.size() == 0) {
                log.error("Gemini 응답에 candidates가 없음");
                return "";
            }

            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts.isEmpty() || !parts.isArray() || parts.size() == 0) {
                return "";
            }

            return parts.get(0).path("text").asText("");

        } catch (Exception e) {
            log.error("Gemini 응답 파싱 실패: ", e);
            return "";
        }
    }

    @Override
    public String getProviderName() {
        return "Gemini OCR";
    }
}

package com.myyak.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini LLM 어댑터
 * AI 약물 분석에 사용되는 LLM 클라이언트 구현체
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiLlmAdapter implements LlmClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    @Value("${ai.gemini.model:gemini-2.0-flash}")
    private String model;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("Gemini API key가 설정되지 않음");
            throw new RuntimeException("Gemini API key가 설정되지 않았습니다.");
        }

        try {
            String combinedPrompt = buildCombinedPrompt(systemPrompt, userPrompt);
            Map<String, Object> requestBody = buildGeminiRequest(combinedPrompt);
            String geminiUrl = GEMINI_API_URL + model + ":generateContent?key=" + apiKey;

            log.info("[Gemini] 모델: {}, 프롬프트 길이: {}", model, combinedPrompt.length());
            long startTime = System.currentTimeMillis();

            String response = webClient.post()
                    .uri(geminiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .map(body -> {
                                        log.error("[Gemini] API 에러 응답: {}", body);
                                        return new RuntimeException("Gemini API error: " + body);
                                    }))
                    .bodyToMono(String.class)
                    .block();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[Gemini] 응답 완료: {}ms", elapsed);

            return extractTextFromResponse(response);

        } catch (Exception e) {
            log.error("[Gemini] 생성 실패: ", e);
            throw new RuntimeException("LLM 호출 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "Gemini";
    }

    @Override
    public String getModelName() {
        return model;
    }

    private String buildCombinedPrompt(String systemPrompt, String userPrompt) {
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            return userPrompt;
        }
        return systemPrompt + "\n\n---\n\n" + userPrompt;
    }

    private Map<String, Object> buildGeminiRequest(String prompt) {
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", prompt);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(textPart));

        Map<String, Object> request = new HashMap<>();
        request.put("contents", List.of(content));

        // 생성 설정
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("maxOutputTokens", 8192);
        generationConfig.put("temperature", 0.1);  // 일관된 JSON 출력을 위해 낮은 온도

        // Gemini 2.5 Flash thinking 설정
        if (model.contains("2.5-flash") && !model.contains("lite")) {
            Map<String, Object> thinkingConfig = new HashMap<>();
            thinkingConfig.put("thinkingBudget", -1);
            generationConfig.put("thinkingConfig", thinkingConfig);
        }

        request.put("generationConfig", generationConfig);

        return request;
    }

    private String extractTextFromResponse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);

        // 에러 체크
        JsonNode error = root.path("error");
        if (!error.isMissingNode()) {
            String errorMessage = error.path("message").asText();
            log.error("[Gemini] API 에러: {}", errorMessage);
            throw new RuntimeException("Gemini API error: " + errorMessage);
        }

        // candidates 추출
        JsonNode candidates = root.path("candidates");
        if (candidates.isEmpty() || !candidates.isArray() || candidates.size() == 0) {
            log.error("[Gemini] 응답에 candidates가 없음");
            throw new RuntimeException("Gemini 응답이 비어있습니다.");
        }

        // finishReason 로깅
        String finishReason = candidates.get(0).path("finishReason").asText("UNKNOWN");
        log.debug("[Gemini] finishReason: {}", finishReason);

        // 토큰 사용량 로깅
        JsonNode usageMetadata = root.path("usageMetadata");
        if (!usageMetadata.isMissingNode()) {
            log.info("[Gemini] 토큰 - prompt: {}, candidates: {}, total: {}",
                    usageMetadata.path("promptTokenCount").asInt(),
                    usageMetadata.path("candidatesTokenCount").asInt(),
                    usageMetadata.path("totalTokenCount").asInt());
        }

        // 텍스트 추출
        String text = candidates.get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText();

        log.debug("[Gemini] 응답 텍스트 길이: {}", text.length());

        return text;
    }
}

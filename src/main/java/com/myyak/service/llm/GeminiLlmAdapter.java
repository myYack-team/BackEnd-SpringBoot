package com.myyak.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myyak.domain.AppSetting;
import com.myyak.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gemini LLM 어댑터
 * AI 약물 분석에 사용되는 LLM 클라이언트 구현체
 * DB에서 모델 설정을 조회하며, 폴백 기능 지원
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiLlmAdapter implements LlmClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AppSettingRepository appSettingRepository;

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    @Value("${ai.gemini.model:gemini-3.5-flash}")
    private String defaultModel;

    @Value("${ai.gemini.analysis-model:gemini-3.1-pro-preview}")
    private String configAnalysisModel;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String DEFAULT_FALLBACK_MODEL = "gemini-3.5-flash";
    private static final Duration LLM_API_TIMEOUT = Duration.ofSeconds(120);

    /** 모델 설정 캐시 TTL (관리자가 설정을 변경하면 이 시간 내에 반영됨) */
    private static final long SETTINGS_CACHE_TTL_MILLIS = 30_000L;

    /** 모델 설정 캐시 (TTL 내 재사용, 만료 시 DB에서 1회 일괄 재조회) */
    private volatile ModelSettings cachedSettings;

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        ModelSettings settings = getModelSettings();
        String primaryModel = settings.analysisModel();
        String fallbackModel = settings.fallbackModel();
        boolean fallbackEnabled = settings.fallbackEnabled();

        log.info("[Gemini] 모델 설정 - primary: {}, fallback: {}, enabled: {}",
                primaryModel, fallbackModel, fallbackEnabled);

        try {
            return generateWithModel(systemPrompt, userPrompt, primaryModel);
        } catch (Exception e) {
            // 503 Service Unavailable 또는 기타 오류 시 폴백
            if (fallbackEnabled && shouldFallback(e)) {
                log.warn("[Gemini] 주 모델 {} 실패, 폴백 모델 {} 로 재시도: {}",
                        primaryModel, fallbackModel, e.getMessage());
                return generateWithModel(systemPrompt, userPrompt, fallbackModel);
            }
            throw e;
        }
    }

    /**
     * 폴백을 시도해야 하는 예외인지 판단
     */
    private boolean shouldFallback(Exception e) {
        // 503 Service Unavailable (모델 과부하)
        if (e instanceof WebClientResponseException.ServiceUnavailable) {
            return true;
        }
        // 404 Not Found (모델 지원 종료)
        if (e instanceof WebClientResponseException.NotFound) {
            return true;
        }
        // RuntimeException 메시지에 503/404 또는 overloaded 포함
        String message = e.getMessage();
        if (message != null) {
            return message.contains("503") ||
                    message.contains("404") ||
                    message.toLowerCase().contains("overload") ||
                    message.toLowerCase().contains("unavailable") ||
                    message.toLowerCase().contains("not found");
        }
        return false;
    }

    /**
     * 모델 설정 조회 (TTL 캐시 적용)
     * TTL 내에는 캐시를 재사용하고, 만료 시 DB에서 1회 일괄 조회합니다.
     */
    private ModelSettings getModelSettings() {
        ModelSettings settings = cachedSettings;
        long now = System.currentTimeMillis();
        if (settings != null && now - settings.loadedAt() < SETTINGS_CACHE_TTL_MILLIS) {
            return settings;
        }
        settings = loadModelSettings(now);
        cachedSettings = settings;
        return settings;
    }

    /**
     * DB에서 모델 관련 설정을 1회 쿼리로 일괄 조회, 없는 키는 기본값 사용
     */
    private ModelSettings loadModelSettings(long now) {
        Map<String, String> values = appSettingRepository.findBySettingKeyIn(List.of(
                        AppSetting.KEY_GEMINI_ANALYSIS_MODEL,
                        AppSetting.KEY_GEMINI_ANALYSIS_FALLBACK_MODEL,
                        AppSetting.KEY_GEMINI_FALLBACK_ENABLED)).stream()
                .collect(Collectors.toMap(AppSetting::getSettingKey, AppSetting::getSettingValue));

        String analysisModel = values.getOrDefault(AppSetting.KEY_GEMINI_ANALYSIS_MODEL, configAnalysisModel);
        String fallbackModel = values.getOrDefault(AppSetting.KEY_GEMINI_ANALYSIS_FALLBACK_MODEL, DEFAULT_FALLBACK_MODEL);
        String fallbackEnabledValue = values.get(AppSetting.KEY_GEMINI_FALLBACK_ENABLED);
        boolean fallbackEnabled = fallbackEnabledValue == null || Boolean.parseBoolean(fallbackEnabledValue);

        return new ModelSettings(analysisModel, fallbackModel, fallbackEnabled, now);
    }

    /**
     * 모델 설정 스냅샷 (캐시 항목)
     */
    private record ModelSettings(
            String analysisModel,
            String fallbackModel,
            boolean fallbackEnabled,
            long loadedAt) {
    }

    /**
     * 지정된 모델로 텍스트 생성
     */
    private String generateWithModel(String systemPrompt, String userPrompt, String model) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("Gemini API key가 설정되지 않음");
            throw new RuntimeException("Gemini API key가 설정되지 않았습니다.");
        }

        try {
            String combinedPrompt = buildCombinedPrompt(systemPrompt, userPrompt);
            Map<String, Object> requestBody = buildGeminiRequest(combinedPrompt, model);
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
                    .block(LLM_API_TIMEOUT);

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
        return getModelSettings().analysisModel();
    }

    private String buildCombinedPrompt(String systemPrompt, String userPrompt) {
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            return userPrompt;
        }
        return systemPrompt + "\n\n---\n\n" + userPrompt;
    }

    private Map<String, Object> buildGeminiRequest(String prompt, String model) {
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

        // Gemini 2.5 Flash thinking 설정 (lite 제외)
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
            log.error("[Gemini] 응답에 candidates가 없음. 전체 응답: {}", response);
            throw new RuntimeException("Gemini 응답이 비어있습니다.");
        }

        JsonNode firstCandidate = candidates.get(0);

        // finishReason 로깅 (SAFETY로 차단된 경우 확인)
        String finishReason = firstCandidate.path("finishReason").asText("UNKNOWN");
        log.info("[Gemini] finishReason: {}", finishReason);

        // 안전 필터에 의해 차단된 경우
        if ("SAFETY".equals(finishReason) || "BLOCKED".equals(finishReason)) {
            JsonNode safetyRatings = firstCandidate.path("safetyRatings");
            log.error("[Gemini] 안전 필터에 의해 차단됨. safetyRatings: {}", safetyRatings);
            throw new RuntimeException("Gemini 응답이 안전 필터에 의해 차단되었습니다.");
        }

        // 토큰 사용량 로깅
        JsonNode usageMetadata = root.path("usageMetadata");
        if (!usageMetadata.isMissingNode()) {
            log.info("[Gemini] 토큰 - prompt: {}, candidates: {}, total: {}",
                    usageMetadata.path("promptTokenCount").asInt(),
                    usageMetadata.path("candidatesTokenCount").asInt(),
                    usageMetadata.path("totalTokenCount").asInt());
        }

        // content 추출
        JsonNode content = firstCandidate.path("content");
        if (content.isMissingNode()) {
            log.error("[Gemini] content가 없음. candidate: {}", firstCandidate);
            throw new RuntimeException("Gemini 응답에 content가 없습니다. finishReason: " + finishReason);
        }

        // parts 추출
        JsonNode parts = content.path("parts");
        if (parts.isEmpty() || !parts.isArray() || parts.size() == 0) {
            log.error("[Gemini] parts가 비어있음. content: {}", content);
            throw new RuntimeException("Gemini 응답의 parts가 비어있습니다. finishReason: " + finishReason);
        }

        JsonNode firstPart = parts.get(0);
        if (firstPart == null) {
            log.error("[Gemini] parts[0]이 null. parts: {}", parts);
            throw new RuntimeException("Gemini 응답의 parts[0]이 null입니다.");
        }

        // 텍스트 추출
        String text = firstPart.path("text").asText("");

        if (text.isEmpty()) {
            log.warn("[Gemini] 텍스트가 비어있음. firstPart: {}", firstPart);
        }

        log.debug("[Gemini] 응답 텍스트 길이: {}", text.length());

        return text;
    }
}

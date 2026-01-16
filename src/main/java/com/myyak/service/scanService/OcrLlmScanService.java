package com.myyak.service.scanService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.domain.DrugInfo;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.service.drugSearchService.DrugSearchService;
import com.myyak.service.ocr.OcrClient;
import com.myyak.service.ocr.OcrResult;
import com.myyak.util.PrescriptionMaskingUtil;
import com.myyak.web.dto.ScanDTO.ScanResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR + LLM 텍스트 파싱 방식의 스캔 서비스
 * - Phase 1: Google Vision OCR로 텍스트 추출 (~2-3초)
 * - Phase 2: LLM에 텍스트만 전달하여 구조화 (~3-5초)
 * - 총 예상 시간: ~5-8초
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scan.strategy", havingValue = "ocr-llm", matchIfMissing = false)
public class OcrLlmScanService implements ScanService {

    private final OcrClient ocrClient;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final DrugSearchService drugSearchService;

    // LLM 프로바이더 설정
    @Value("${scan.llm-provider:gemini}")
    private String llmProvider;  // gemini | openai

    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.gemini.model:gemini-2.0-flash}")
    private String geminiModel;

    @Value("${ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${ai.openai.model:gpt-4o-mini}")
    private String openaiModel;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    // OCR 텍스트를 구조화하는 LLM 프롬프트
    private static final String STRUCTURE_PROMPT = """
            당신은 한국 처방전/약봉투 텍스트를 분석하는 전문 약사입니다.

            아래는 처방전 또는 약봉투에서 OCR로 추출한 텍스트입니다.
            이 텍스트를 분석하여 JSON 형식으로 구조화하세요.

            ## OCR 텍스트:
            ```
            %s
            ```

            ## 추출해야 할 정보:
            1. patientName: 환자 이름 (없으면 null)
            2. hospitalName: 병원/약국 이름 (없으면 null)
            3. diagnosis: 진단명 또는 증상 (명시되지 않으면 약물 조합으로 추론)
            4. durationDays: 투약일수 (숫자만, 없으면 null)
            5. medications: 약물 목록 배열

            ## 약물(medications) 추출 규칙:
            - 실제 의약품명만 추출 (금액, 조제료, 약국명 등 제외)
            - 한국 의약품 데이터베이스에 등록된 약품명 형식으로 정규화
            - 예: "록사틴정500mg" → "록사틴정500밀리그램(록시트로마이신)"

            각 약물에 대해:
            - name: 약품명 (정제, 캡슐, 시럽 등 제형 포함)
            - dosage: 1회 복용량 (숫자만, 예: 1, 2)
            - frequency: 하루 복용 횟수 (숫자만, 예: 1, 2, 3)
            - timings: 복용 시간대 배열
            - durationDays: 복용 일수 (숫자만)
            - totalCount: 총 수량 (숫자만)

            ## timings 값 (해당하는 것만 배열에 포함):
            - MORNING: 아침
            - AFTERNOON: 점심
            - EVENING: 저녁
            - AS_NEEDED: 필요시

            ## 출력 형식 (JSON만 출력, 설명 없음):
            {
              "patientName": "홍길동",
              "hospitalName": "서울내과의원",
              "diagnosis": "급성 상기도 감염",
              "durationDays": 7,
              "medications": [
                {
                  "name": "록사틴정500밀리그램",
                  "dosage": 1,
                  "frequency": 2,
                  "timings": ["MORNING", "EVENING"],
                  "durationDays": 7,
                  "totalCount": 14
                }
              ]
            }
            """;

    @Override
    public ScanResponseDTO.ScanResult scanPrescription(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new GeneralException(ErrorStatus.SCAN_IMAGE_REQUIRED);
        }

        long totalStartTime = System.currentTimeMillis();

        try {
            byte[] imageBytes = image.getBytes();

            // Phase 1: OCR 텍스트 추출 (Google Vision)
            long ocrStartTime = System.currentTimeMillis();
            OcrResult ocrResult = ocrClient.extractText(imageBytes);
            long ocrElapsed = System.currentTimeMillis() - ocrStartTime;
            log.info("OCR 완료: {}ms, 텍스트 길이: {}, 제공자: {}",
                    ocrElapsed,
                    ocrResult.getFullText() != null ? ocrResult.getFullText().length() : 0,
                    ocrClient.getProviderName());

            if (!ocrResult.hasText()) {
                return createErrorResponse("이미지에서 텍스트를 추출할 수 없습니다.");
            }

            // Phase 2: 민감 정보 마스킹 후 LLM 전송
            String rawText = ocrResult.getFullText();
            String maskedText = PrescriptionMaskingUtil.maskSensitiveInfo(rawText);
            log.debug("OCR 추출 텍스트 길이: {}", maskedText.length());

            // Phase 3: LLM으로 텍스트 구조화
            long llmStartTime = System.currentTimeMillis();
            ScanResponseDTO.ScanResult result = structureWithLlm(maskedText);
            long llmElapsed = System.currentTimeMillis() - llmStartTime;
            log.info("LLM 구조화 완료: {}ms", llmElapsed);

            long totalElapsed = System.currentTimeMillis() - totalStartTime;
            log.info("OCR+LLM 스캔 총 소요시간: {}ms ({}초)", totalElapsed, totalElapsed / 1000.0);

            return result;

        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            log.error("OCR+LLM 스캔 실패: ", e);
            throw new GeneralException(ErrorStatus.VISION_API_ERROR);
        }
    }

    private ScanResponseDTO.ScanResult structureWithLlm(String ocrText) {
        log.info("LLM 프로바이더: {}", llmProvider);

        if ("openai".equalsIgnoreCase(llmProvider)) {
            return structureWithOpenAI(ocrText);
        } else {
            return structureWithGemini(ocrText);
        }
    }

    private ScanResponseDTO.ScanResult structureWithGemini(String ocrText) {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            log.error("Gemini API key가 설정되지 않음");
            return createErrorResponse("AI 서비스가 설정되지 않았습니다.");
        }

        try {
            String prompt = String.format(STRUCTURE_PROMPT, ocrText);
            Map<String, Object> requestBody = buildGeminiRequest(prompt);
            String geminiUrl = GEMINI_API_URL + geminiModel + ":generateContent?key=" + geminiApiKey;

            log.info("Gemini 모델: {}", geminiModel);
            log.debug("Gemini 요청 본문: {}", objectMapper.writeValueAsString(requestBody));

            String response = webClient.post()
                    .uri(geminiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .map(body -> {
                                        log.error("Gemini API 에러 응답: {}", body);
                                        return new RuntimeException("Gemini API error: " + body);
                                    }))
                    .bodyToMono(String.class)
                    .block();

            return parseGeminiResponse(response);

        } catch (Exception e) {
            log.error("Gemini LLM 구조화 실패: ", e);
            return createErrorResponse("처방전 분석에 실패했습니다.");
        }
    }

    private ScanResponseDTO.ScanResult structureWithOpenAI(String ocrText) {
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            log.error("OpenAI API key가 설정되지 않음");
            return createErrorResponse("AI 서비스가 설정되지 않았습니다.");
        }

        try {
            String prompt = String.format(STRUCTURE_PROMPT, ocrText);
            Map<String, Object> requestBody = buildOpenAIRequest(prompt);

            log.info("OpenAI 모델: {}", openaiModel);
            log.debug("OpenAI 요청 본문: {}", objectMapper.writeValueAsString(requestBody));

            String response = webClient.post()
                    .uri(OPENAI_API_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .map(body -> {
                                        log.error("OpenAI API 에러 응답: {}", body);
                                        return new RuntimeException("OpenAI API error: " + body);
                                    }))
                    .bodyToMono(String.class)
                    .block();

            return parseOpenAIResponse(response);

        } catch (Exception e) {
            log.error("OpenAI LLM 구조화 실패: ", e);
            return createErrorResponse("처방전 분석에 실패했습니다.");
        }
    }

    private Map<String, Object> buildOpenAIRequest(String prompt) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", openaiModel);
        request.put("temperature", 0.1);
        request.put("max_tokens", 4096);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);

        request.put("messages", messages);

        return request;
    }

    private ScanResponseDTO.ScanResult parseOpenAIResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            // 에러 체크
            JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                log.error("OpenAI API 에러: {}", error.path("message").asText());
                return createErrorResponse("AI 분석 중 오류가 발생했습니다.");
            }

            // choices 추출
            JsonNode choices = root.path("choices");
            if (choices.isEmpty() || !choices.isArray() || choices.size() == 0) {
                log.error("OpenAI 응답에 choices가 없음");
                return createErrorResponse("AI 응답을 받지 못했습니다.");
            }

            // finish_reason 확인 (디버깅용)
            String finishReason = choices.get(0).path("finish_reason").asText("UNKNOWN");
            log.info("OpenAI finish_reason: {}", finishReason);

            // 토큰 사용량 확인
            JsonNode usage = root.path("usage");
            if (!usage.isMissingNode()) {
                log.info("OpenAI 토큰 사용량 - prompt: {}, completion: {}, total: {}",
                        usage.path("prompt_tokens").asInt(),
                        usage.path("completion_tokens").asInt(),
                        usage.path("total_tokens").asInt());
            }

            String text = choices.get(0)
                    .path("message")
                    .path("content")
                    .asText();

            log.debug("OpenAI 응답 텍스트 길이: {}", text.length());

            // JSON 추출 및 파싱 (공통 로직 사용)
            String jsonText = extractJsonFromText(text);
            log.debug("LLM 응답 JSON:\n{}", jsonText);

            return parseJsonToResult(jsonText);

        } catch (Exception e) {
            log.error("OpenAI 응답 파싱 실패: ", e);
            return createErrorResponse("AI 응답 분석에 실패했습니다.");
        }
    }

    private ScanResponseDTO.ScanResult parseJsonToResult(String jsonText) throws Exception {
        JsonNode resultJson = objectMapper.readTree(jsonText);

        String patientName = getTextOrNull(resultJson, "patientName");
        String hospitalName = getTextOrNull(resultJson, "hospitalName");
        String diagnosis = getTextOrNull(resultJson, "diagnosis");
        Integer durationDays = getIntOrNull(resultJson, "durationDays");

        List<ScanResponseDTO.ScannedMedication> medications = new ArrayList<>();
        JsonNode medicationsNode = resultJson.path("medications");

        if (medicationsNode.isArray()) {
            for (JsonNode medNode : medicationsNode) {
                ScanResponseDTO.ScannedMedication medication = parseMedication(medNode);
                if (medication != null) {
                    medications.add(medication);
                }
            }
        }

        ScanResponseDTO.ScanResult result = ScanResponseDTO.ScanResult.builder()
                .success(true)
                .confidence(medications.isEmpty() ? "low" : "high")
                .patientName(patientName)
                .hospitalName(hospitalName)
                .diagnosis(diagnosis)
                .durationDays(durationDays)
                .medications(medications)
                .notes(null)
                .build();

        return maskResponseSensitiveInfo(result);
    }

    private Map<String, Object> buildGeminiRequest(String prompt) {
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", prompt);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(textPart));

        Map<String, Object> request = new HashMap<>();
        request.put("contents", List.of(content));

        // 빠른 응답을 위한 설정
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("maxOutputTokens", 16384);
        generationConfig.put("temperature", 0.1);

        // Gemini 2.5 Flash thinking 설정 (generationConfig 내부에 위치해야 함)
        // gemini-2.5-flash-lite는 thinking이 기본 비활성화이므로 설정 제외
        // gemini-2.5-flash는 thinking 설정 필요
        if (geminiModel.contains("2.5-flash") && !geminiModel.contains("lite")) {
            Map<String, Object> thinkingConfig = new HashMap<>();
            thinkingConfig.put("thinkingBudget", -1);  // 동적 사고 활성화
            generationConfig.put("thinkingConfig", thinkingConfig);
        }

        request.put("generationConfig", generationConfig);

        return request;
    }

    private ScanResponseDTO.ScanResult parseGeminiResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            // 에러 체크
            JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                log.error("Gemini API 에러: {}", error.path("message").asText());
                return createErrorResponse("AI 분석 중 오류가 발생했습니다.");
            }

            // candidates 추출
            JsonNode candidates = root.path("candidates");
            if (candidates.isEmpty() || !candidates.isArray() || candidates.size() == 0) {
                log.error("Gemini 응답에 candidates가 없음");
                return createErrorResponse("AI 응답을 받지 못했습니다.");
            }

            // finishReason 확인 (디버깅용)
            String finishReason = candidates.get(0).path("finishReason").asText("UNKNOWN");
            log.info("Gemini finishReason: {}", finishReason);

            // 토큰 사용량 확인
            JsonNode usageMetadata = root.path("usageMetadata");
            if (!usageMetadata.isMissingNode()) {
                log.info("Gemini 토큰 사용량 - prompt: {}, candidates: {}, total: {}",
                        usageMetadata.path("promptTokenCount").asInt(),
                        usageMetadata.path("candidatesTokenCount").asInt(),
                        usageMetadata.path("totalTokenCount").asInt());
            }

            String text = candidates.get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();

            log.debug("Gemini 응답 텍스트 길이: {}", text.length());

            // JSON 추출
            String jsonText = extractJsonFromText(text);
            log.debug("LLM 응답 JSON:\n{}", jsonText);

            JsonNode resultJson = objectMapper.readTree(jsonText);

            // 결과 파싱
            String patientName = getTextOrNull(resultJson, "patientName");
            String hospitalName = getTextOrNull(resultJson, "hospitalName");
            String diagnosis = getTextOrNull(resultJson, "diagnosis");
            Integer durationDays = getIntOrNull(resultJson, "durationDays");

            // 약물 목록 파싱
            List<ScanResponseDTO.ScannedMedication> medications = new ArrayList<>();
            JsonNode medicationsNode = resultJson.path("medications");

            if (medicationsNode.isArray()) {
                for (JsonNode medNode : medicationsNode) {
                    ScanResponseDTO.ScannedMedication medication = parseMedication(medNode);
                    if (medication != null) {
                        medications.add(medication);
                    }
                }
            }

            ScanResponseDTO.ScanResult result = ScanResponseDTO.ScanResult.builder()
                    .success(true)
                    .confidence(medications.isEmpty() ? "low" : "high")
                    .patientName(patientName)
                    .hospitalName(hospitalName)
                    .diagnosis(diagnosis)
                    .durationDays(durationDays)
                    .medications(medications)
                    .notes(null)
                    .build();

            return maskResponseSensitiveInfo(result);

        } catch (Exception e) {
            log.error("Gemini 응답 파싱 실패: ", e);
            return createErrorResponse("AI 응답 분석에 실패했습니다.");
        }
    }

    private ScanResponseDTO.ScannedMedication parseMedication(JsonNode node) {
        try {
            String name = node.path("name").asText();
            if (name == null || name.isEmpty()) {
                return null;
            }

            // timings 파싱
            List<MedicationTiming> timings = new ArrayList<>();
            JsonNode timingsNode = node.path("timings");
            if (timingsNode.isArray()) {
                for (JsonNode t : timingsNode) {
                    try {
                        timings.add(MedicationTiming.valueOf(t.asText()));
                    } catch (IllegalArgumentException ignored) {
                        log.debug("알 수 없는 timing 값: {}", t.asText());
                    }
                }
            }

            // 기본 timings 설정 (비어있으면)
            if (timings.isEmpty()) {
                timings.add(MedicationTiming.MORNING);
            }

            // 빌더 생성
            ScanResponseDTO.ScannedMedication.ScannedMedicationBuilder builder = ScanResponseDTO.ScannedMedication.builder()
                    .name(name)
                    .dosage(getIntOrNull(node, "dosage"))
                    .frequency(getIntOrNull(node, "frequency"))
                    .timings(timings)
                    .durationDays(getIntOrNull(node, "durationDays"))
                    .totalCount(getIntOrNull(node, "totalCount"));

            // DB 매칭 로직 추가
            DrugInfo matchedDrug = matchDrugFromDatabase(name);

            if (matchedDrug != null) {
                // DB 매칭 성공 - 정확한 약물 정보로 보강
                builder.name(extractPureDrugName(matchedDrug.getItemName()))
                       .drugItemSeq(matchedDrug.getItemSeq())
                       .ingredient(resolveIngredient(matchedDrug.getItemName(), matchedDrug.getIngredientName()))
                       .efficacy(matchedDrug.getEfficacy())
                       .imageUrl(matchedDrug.getImageUrl())
                       .entpName(matchedDrug.getEntpName());
                log.info("약물 '{}' → DB 매칭: '{}'", name, matchedDrug.getItemName());
            } else {
                // DB 매칭 실패 → 편집거리 검색으로 OCR 오타 보정
                Optional<DrugInfo> editDistanceMatch = drugSearchService.findByEditDistance(name);
                if (editDistanceMatch.isPresent()) {
                    DrugInfo foundDrug = editDistanceMatch.get();
                    builder.name(extractPureDrugName(foundDrug.getItemName()))
                           .drugItemSeq(foundDrug.getItemSeq())
                           .ingredient(resolveIngredient(foundDrug.getItemName(), foundDrug.getIngredientName()))
                           .efficacy(foundDrug.getEfficacy())
                           .imageUrl(foundDrug.getImageUrl())
                           .entpName(foundDrug.getEntpName())
                           .matchedByEditDistance(true);
                    log.info("약물 '{}' → 편집거리 매칭: '{}'", name, foundDrug.getItemName());
                } else {
                    log.warn("약물 '{}' → DB 매칭 실패", name);
                }
            }

            return builder.build();

        } catch (Exception e) {
            log.warn("약물 파싱 실패: ", e);
            return null;
        }
    }

    /**
     * 메모리 캐시에서 약물 검색 (DB 쿼리 없음, 매우 빠름)
     */
    private DrugInfo matchDrugFromDatabase(String drugName) {
        if (drugName == null || drugName.isBlank()) {
            return null;
        }

        // 약물명에서 검색 키워드 추출 (용량, 제형 제외)
        String searchKeyword = extractDrugNameForSearch(drugName);

        // 1. 메모리 캐시에서 부분 일치 검색
        Optional<DrugInfo> cacheMatch = drugSearchService.findByNameContaining(searchKeyword);
        if (cacheMatch.isPresent()) {
            return cacheMatch.get();
        }

        // 2. 약물명 정규화 후 캐시에서 검색
        String normalizedName = normalizeDrugName(drugName);
        return drugSearchService.findByNameContaining(normalizedName).orElse(null);
    }

    /**
     * 검색용 약물명 추출 (용량, 단위, 공백 제거)
     * DB의 약물명은 공백 없이 저장되어 있으므로 검색 시에도 공백 제거
     */
    private String extractDrugNameForSearch(String drugName) {
        // "디푸코연고 0.3% 10g" → "디푸코연고0.3%"
        // "메치론정 4mg" → "메치론정"
        // "셀벡스 캡슐 50mg" → "셀벡스"
        String cleaned = drugName
                .replaceAll("\\s+", "")  // 모든 공백 제거 (DB는 공백 없이 저장됨)
                .replaceAll("\\d+(\\.\\d+)?(mg|ml|g|밀리그램|그램)$", "")  // 끝의 용량 제거
                .replaceAll("\\d+g$", "");  // "10g" 같은 용량 제거
        return cleaned.isEmpty() ? drugName.replaceAll("\\s+", "") : cleaned;
    }

    /**
     * 약물명 정규화
     */
    private String normalizeDrugName(String drugName) {
        return drugName
                .replaceAll("\\s+", "")
                .replaceAll("mg", "밀리그램")
                .replaceAll("ml", "밀리리터")
                .replaceAll("g", "그램")
                .toLowerCase();
    }

    /**
     * DB에서 가져온 약물명에서 순수 약물명 추출
     * 예: "타이레놀정500밀리그램(아세트아미노펜)" → "타이레놀정500밀리그램"
     */
    private String extractPureDrugName(String fullName) {
        if (fullName == null) return null;
        int parenIdx = fullName.indexOf('(');
        if (parenIdx > 0) {
            return fullName.substring(0, parenIdx).trim();
        }
        return fullName.trim();
    }

    /**
     * 성분명 결정 (괄호 안 성분명 또는 ingredientName 필드)
     */
    private String resolveIngredient(String itemName, String ingredientName) {
        // 1. 약품명에서 괄호 안의 성분명 추출 시도
        if (itemName != null) {
            Pattern pattern = Pattern.compile("\\(([^)]+)\\)");
            Matcher matcher = pattern.matcher(itemName);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        // 2. ingredientName 필드 사용
        return ingredientName;
    }

    private String extractJsonFromText(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            String json = text.substring(start, end + 1);
            // 괄호가 균형을 이루는지 확인하고 아니면 복구 시도
            if (!isBalanced(json)) {
                log.warn("JSON 괄호 불균형 감지, 복구 시도");
                return repairIncompleteJson(json);
            }
            return json;
        }
        // JSON이 불완전한 경우 (응답이 잘린 경우) 복구 시도
        if (start >= 0) {
            String partial = text.substring(start);
            return repairIncompleteJson(partial);
        }
        return text;
    }

    private boolean isBalanced(String json) {
        int braceCount = 0;
        int bracketCount = 0;
        for (char c : json.toCharArray()) {
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;
            else if (c == '[') bracketCount++;
            else if (c == ']') bracketCount--;
        }
        return braceCount == 0 && bracketCount == 0;
    }

    /**
     * 불완전한 JSON 복구 시도
     * LLM 응답이 중간에 잘린 경우를 처리
     */
    private String repairIncompleteJson(String json) {
        // 열린 괄호 카운트
        int braceCount = 0;
        int bracketCount = 0;

        for (char c : json.toCharArray()) {
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;
            else if (c == '[') bracketCount++;
            else if (c == ']') bracketCount--;
        }

        StringBuilder repaired = new StringBuilder(json);

        // 불완전한 문자열 종료 처리 (마지막이 따옴표 없이 끝난 경우)
        String trimmed = json.trim();
        if (!trimmed.endsWith("}") && !trimmed.endsWith("]") && !trimmed.endsWith("\"") && !trimmed.endsWith(",")) {
            // 문자열 중간에서 잘린 경우 - 따옴표로 닫기
            repaired.append("\"");
        }

        // 닫히지 않은 배열 닫기
        for (int i = 0; i < bracketCount; i++) {
            repaired.append("]");
        }

        // 닫히지 않은 객체 닫기
        for (int i = 0; i < braceCount; i++) {
            repaired.append("}");
        }

        log.debug("JSON 복구 시도: 원본 길이={}, 복구 후 길이={}", json.length(), repaired.length());
        return repaired.toString();
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        if (fieldNode.isNull() || fieldNode.isMissingNode()) {
            return null;
        }
        String value = fieldNode.asText();
        return value.isEmpty() || "null".equals(value) ? null : value;
    }

    private Integer getIntOrNull(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        if (fieldNode.isNull() || fieldNode.isMissingNode() || !fieldNode.isNumber()) {
            return null;
        }
        return fieldNode.asInt();
    }

    private ScanResponseDTO.ScanResult createErrorResponse(String message) {
        return ScanResponseDTO.ScanResult.builder()
                .success(false)
                .confidence("low")
                .medications(List.of())
                .notes(message)
                .build();
    }

    /**
     * 클라이언트에 반환하기 전 응답 내 민감 정보 마스킹
     */
    private ScanResponseDTO.ScanResult maskResponseSensitiveInfo(ScanResponseDTO.ScanResult result) {
        if (result == null) {
            return null;
        }

        String maskedName = maskPatientName(result.getPatientName());

        return ScanResponseDTO.ScanResult.builder()
                .success(result.getSuccess())
                .confidence(result.getConfidence())
                .patientName(maskedName)
                .hospitalName(result.getHospitalName())
                .diagnosis("***")  // 진단명은 민감 정보이므로 마스킹
                .durationDays(result.getDurationDays())
                .medications(result.getMedications())
                .notes(result.getNotes())
                .build();
    }

    /**
     * 환자 이름 마스킹 (홍길동 -> 홍*동)
     */
    private String maskPatientName(String name) {
        if (name == null || name.length() <= 2) {
            return "***";
        }
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }
}

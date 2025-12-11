package com.myyak.service.scanService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.domain.DrugInfo;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.repository.DrugInfoRepository;
import com.myyak.service.embeddingService.EmbeddingService;
import com.myyak.web.dto.EmbeddingDTO.EmbeddingResponseDTO;
import com.myyak.web.dto.ScanDTO.ScanResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanServiceImpl implements ScanService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final DrugInfoRepository drugInfoRepository;
    private final EmbeddingService embeddingService;

    @Value("${ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${ai.openai.model:gpt-4o-mini}")
    private String openaiModel;

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final double SIMILAR_DRUG_MIN_SIMILARITY = 0.5;
    private static final String DEBUG_IMAGE_DIR = "C:\\tmp\\scan_debug";

    private static final String VISION_PROMPT = """
            당신은 한국 처방전/약봉투 이미지를 분석하는 전문 약사입니다.

            이미지에서 의약품 정보를 추출하여 JSON 형식으로 반환하세요.

            ## 중요: confidence 판단 기준
            - "high": 1개 이상의 약품명을 명확히 읽을 수 있음
            - "medium": 약품명이 일부 흐리거나 추론이 필요함
            - "low": 이미지에 약품 정보가 전혀 없거나 완전히 읽을 수 없음

            약품명이 하나라도 보이면 success: true, confidence: "high" 또는 "medium"으로 설정하세요.
            처방전/약봉투가 아닌 이미지이거나 텍스트가 전혀 없을 때만 confidence: "low"로 설정하세요.

            ## 출력 형식:
            {
              "success": true,
              "confidence": "high",
              "medications": [
                {
                  "name": "메드론정",
                  "dosage": 1,
                  "frequency": 2,
                  "timings": ["AFTER_BREAKFAST", "AFTER_DINNER"],
                  "durationDays": 7,
                  "totalCount": 14
                }
              ],
              "notes": null
            }

            ## 필드 설명:
            - name: 순수 약품명만 (예: "메드론정", "아스피린정", "타이레놀정")
            - dosage: 1회 복용량 (알 개수, 기본값 1)
            - frequency: 하루 복용 횟수 (기본값 1)
            - timings: 복용 시간대 배열
            - durationDays: 총 복용 일수 (기본값 7)
            - totalCount: 총 약 개수 (dosage × frequency × durationDays)

            ## 약품명(name) 작성 규칙 (매우 중요):
            - 순수 약품명만 추출하세요
            - 제외할 것: 성분명, 보관방법, 부가설명, 괄호 안 내용
            - 좋은 예: "메드론정", "에이스타정", "아스피린프로텍트정100mg"
            - 나쁜 예: "메드론정 (안전보관)", "에이스타정(위험보관)", "메드론정(메틸프레드니솔론)"
            - 용량(mg, ml 등)은 약품명에 붙어있으면 포함 가능

            ## timings 값 (복용 시간대):
            - BEFORE_BREAKFAST: 아침 식전
            - AFTER_BREAKFAST: 아침 식후
            - BEFORE_LUNCH: 점심 식전
            - AFTER_LUNCH: 점심 식후
            - BEFORE_DINNER: 저녁 식전
            - AFTER_DINNER: 저녁 식후
            - BEFORE_BED: 취침 전
            - AS_NEEDED: 필요시

            ## 복용 시간 해석 규칙:
            - "1일 2회" → frequency: 2, timings: ["AFTER_BREAKFAST", "AFTER_DINNER"]
            - "1일 3회" → frequency: 3, timings: ["AFTER_BREAKFAST", "AFTER_LUNCH", "AFTER_DINNER"]
            - 시간대 정보 없으면 기본값: ["AFTER_BREAKFAST"]

            ## 주의사항:
            1. 반드시 유효한 JSON만 출력 (마크다운 코드블록 없이)
            2. 읽을 수 없는 필드는 기본값 사용
            3. 약품명이 부분적으로만 보여도 최대한 추론해서 입력
            4. 한국어 약품명, 영어 약품명 모두 인식
            """;

    @Override
    public ScanResponseDTO.ScanResult scanPrescription(MultipartFile image) {
        return processScan(image, false);
    }

    @Override
    public ScanResponseDTO.ScanResult scanPrescriptionWithEmbedding(MultipartFile image) {
        return processScan(image, true);
    }

    /**
     * 공통 스캔 처리 메서드
     * @param image 처방전 이미지
     * @param useEmbedding 임베딩 기반 유사 약물 검색 사용 여부
     */
    private ScanResponseDTO.ScanResult processScan(MultipartFile image, boolean useEmbedding) {
        if (image == null || image.isEmpty()) {
            throw new GeneralException(ErrorStatus.SCAN_IMAGE_REQUIRED);
        }

        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }

        // 디버깅용 이미지 저장
        saveDebugImage(image);

        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            log.warn("OpenAI API key is not configured. Returning mock data.");
            return createMockResponse(useEmbedding);
        }

        try {
            byte[] imageBytes = image.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            Map<String, Object> requestBody = buildOpenAIRequest(base64Image, contentType);

            String response = webClient.post()
                    .uri(OPENAI_API_URL)
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseOpenAIResponse(response, useEmbedding);
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            log.error("Vision API error: ", e);
            throw new GeneralException(ErrorStatus.VISION_API_ERROR);
        }
    }

    private Map<String, Object> buildOpenAIRequest(String base64Image, String mimeType) {
        // 이미지 URL (base64 data URL)
        String imageUrl = "data:" + mimeType + ";base64," + base64Image;

        // 이미지 content
        Map<String, Object> imageUrlObj = new HashMap<>();
        imageUrlObj.put("url", imageUrl);

        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        imageContent.put("image_url", imageUrlObj);

        // 텍스트 content
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", VISION_PROMPT);

        // 메시지
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", List.of(textContent, imageContent));

        // 요청 본문
        Map<String, Object> request = new HashMap<>();
        request.put("model", openaiModel);
        request.put("messages", List.of(userMessage));
        request.put("max_tokens", 2000);

        return request;
    }

    private ScanResponseDTO.ScanResult parseOpenAIResponse(String response, boolean useEmbedding) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");

            if (choices.isEmpty()) {
                return createLowConfidenceResponse("AI 응답을 받지 못했습니다.");
            }

            String text = choices.get(0)
                    .path("message")
                    .path("content")
                    .asText();

            log.info("OpenAI Vision 응답: {}", text);

            String jsonText = extractJsonFromText(text);
            JsonNode resultJson = objectMapper.readTree(jsonText);

            boolean success = resultJson.path("success").asBoolean(false);
            String confidence = resultJson.path("confidence").asText("low");
            String notes = resultJson.path("notes").isNull() ? null : resultJson.path("notes").asText();

            List<ScanResponseDTO.ScannedMedication> medications = new ArrayList<>();
            JsonNode medsNode = resultJson.path("medications");

            if (medsNode.isArray()) {
                for (JsonNode medNode : medsNode) {
                    List<MedicationTiming> timings = new ArrayList<>();
                    JsonNode timingsNode = medNode.path("timings");
                    if (timingsNode.isArray()) {
                        for (JsonNode timing : timingsNode) {
                            try {
                                timings.add(MedicationTiming.valueOf(timing.asText()));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }

                    String name = medNode.path("name").asText();

                    // DrugInfo 조회 시도
                    DrugInfo matchedDrug = findDrugInfo(name);

                    ScanResponseDTO.ScannedMedication.ScannedMedicationBuilder builder =
                            ScanResponseDTO.ScannedMedication.builder()
                                    .name(name)
                                    .dosage(medNode.path("dosage").asInt(1))
                                    .frequency(medNode.path("frequency").asInt(1))
                                    .timings(timings)
                                    .durationDays(medNode.path("durationDays").asInt(7))
                                    .totalCount(medNode.path("totalCount").asInt(7));

                    // DrugInfo가 매칭되면 추가 정보 설정 + 매칭된 약물명으로 name 업데이트
                    if (matchedDrug != null) {
                        builder.name(extractPureDrugName(matchedDrug.getItemName()))  // 괄호 제거한 약물명
                               .drugItemSeq(matchedDrug.getItemSeq())
                               .ingredient(resolveIngredient(matchedDrug.getItemName(), matchedDrug.getIngredientName()))  // 괄호 안 한글 우선
                               .efficacy(matchedDrug.getEfficacy())
                               .imageUrl(matchedDrug.getImageUrl())
                               .entpName(matchedDrug.getEntpName())
                               .matchedByEmbedding(false);
                        log.info("약물 '{}' → DB 매칭: '{}'", name, matchedDrug.getItemName());
                    }

                    // 임베딩 모드 + DB 매칭 실패 시 → 가장 유사한 약물 1개를 메인 결과로 설정
                    if (useEmbedding && matchedDrug == null && name != null && !name.isBlank()) {
                        DrugInfo similarDrug = findMostSimilarDrug(name);
                        if (similarDrug != null) {
                            builder.name(extractPureDrugName(similarDrug.getItemName()))  // 괄호 제거한 약물명
                                   .drugItemSeq(similarDrug.getItemSeq())
                                   .ingredient(resolveIngredient(similarDrug.getItemName(), similarDrug.getIngredientName()))  // 괄호 안 한글 우선
                                   .efficacy(similarDrug.getEfficacy())
                                   .imageUrl(similarDrug.getImageUrl())
                                   .entpName(similarDrug.getEntpName())
                                   .matchedByEmbedding(true);
                            log.info("약물 '{}' → 임베딩 매칭: '{}'", name, similarDrug.getItemName());
                        }
                    }

                    medications.add(builder.build());
                }
            }

            return ScanResponseDTO.ScanResult.builder()
                    .success(success)
                    .confidence(confidence)
                    .medications(medications)
                    .notes(notes)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse OpenAI response: ", e);
            return createLowConfidenceResponse("응답 파싱에 실패했습니다.");
        }
    }

    /**
     * 임베딩 기반으로 가장 유사한 약물 1개 찾기
     * @param drugName OCR로 인식된 약물명
     * @return 가장 유사한 DrugInfo (없으면 null)
     */
    private DrugInfo findMostSimilarDrug(String drugName) {
        try {
            EmbeddingResponseDTO.SimilarDrugSearchResult searchResult =
                    embeddingService.searchSimilarDrugs(drugName, 1);

            if (searchResult.getResults().isEmpty()) {
                return null;
            }

            EmbeddingResponseDTO.SimilarDrug topMatch = searchResult.getResults().get(0);

            // 유사도가 최소 기준 이상인 경우에만 반환
            if (topMatch.getSimilarity() < SIMILAR_DRUG_MIN_SIMILARITY) {
                log.info("약물 '{}' 유사도 {}로 기준 미달", drugName, topMatch.getSimilarity());
                return null;
            }

            // DrugInfo 조회
            return drugInfoRepository.findById(topMatch.getItemSeq()).orElse(null);

        } catch (Exception e) {
            log.warn("임베딩 기반 약물 검색 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 약 이름으로 DrugInfo 조회
     */
    private DrugInfo findDrugInfo(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        // 정확한 이름으로 먼저 검색
        Optional<DrugInfo> exact = drugInfoRepository.findByItemName(name);
        if (exact.isPresent()) {
            return exact.get();
        }

        // 부분 일치로 검색
        List<DrugInfo> matches = drugInfoRepository.findByItemNameContaining(name);
        if (!matches.isEmpty()) {
            return matches.get(0);
        }

        // 약 이름에서 숫자/용량 제거 후 재검색
        String simpleName = name.replaceAll("[0-9]+mg|[0-9]+ml|정|캡슐|필름코팅정|서방정", "").trim();
        if (!simpleName.equals(name)) {
            matches = drugInfoRepository.findByItemNameContaining(simpleName);
            if (!matches.isEmpty()) {
                return matches.get(0);
            }
        }

        return null;
    }

    private String extractJsonFromText(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    /**
     * 약물명에서 괄호 안 성분명 제거
     * 예: "메드론정(메틸프레드니솔론)" → "메드론정"
     * 예: "아스피린프로텍트정100mg(아세틸살리실산)" → "아스피린프로텍트정100mg"
     */
    private String extractPureDrugName(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return itemName;
        }
        // 괄호와 그 안의 내용 제거
        return itemName.replaceAll("\\([^)]*\\)", "").trim();
    }

    /**
     * 약물명에서 괄호 안 한글 성분명 추출
     * 예: "메드론정(메틸프레드니솔론)" → "메틸프레드니솔론"
     * 예: "아스피린프로텍트정100mg(아세틸살리실산)" → "아세틸살리실산"
     * 괄호가 없으면 null 반환
     */
    private String extractIngredientFromParentheses(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }
        // 괄호 안의 내용 추출
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\(([^)]+)\\)");
        java.util.regex.Matcher matcher = pattern.matcher(itemName);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * 성분명 결정: 괄호 안 한글 성분명 우선, 없으면 ingredientName 사용
     */
    private String resolveIngredient(String itemName, String ingredientName) {
        String fromParentheses = extractIngredientFromParentheses(itemName);
        if (fromParentheses != null && !fromParentheses.isBlank()) {
            return fromParentheses;
        }
        return ingredientName;
    }

    private ScanResponseDTO.ScanResult createLowConfidenceResponse(String notes) {
        return ScanResponseDTO.ScanResult.builder()
                .success(false)
                .confidence("low")
                .medications(List.of())
                .notes(notes)
                .build();
    }

    /**
     * 디버깅용 이미지 저장
     * @param image 업로드된 이미지
     */
    private void saveDebugImage(MultipartFile image) {
        try {
            // 디렉토리 생성
            Path debugDir = Paths.get(DEBUG_IMAGE_DIR);
            if (!Files.exists(debugDir)) {
                Files.createDirectories(debugDir);
            }

            // 파일명 생성: scan_yyyyMMdd_HHmmss_SSS.확장자
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            String originalFilename = image.getOriginalFilename();
            String extension = "png"; // 기본값
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
            }
            String filename = String.format("scan_%s.%s", timestamp, extension);

            // 파일 저장
            Path filePath = debugDir.resolve(filename);
            Files.write(filePath, image.getBytes());

            log.info("[DEBUG] 스캔 이미지 저장: {} ({}x? bytes: {})",
                    filePath.toAbsolutePath(), image.getContentType(), image.getSize());
        } catch (IOException e) {
            log.warn("[DEBUG] 이미지 저장 실패: {}", e.getMessage());
        }
    }

    private ScanResponseDTO.ScanResult createMockResponse(boolean useEmbedding) {
        return ScanResponseDTO.ScanResult.builder()
                .success(true)
                .confidence("high")
                .medications(List.of(
                        // DB 매칭 성공 예시
                        ScanResponseDTO.ScannedMedication.builder()
                                .name("아스피린프로텍트100mg")
                                .drugItemSeq("200003933")
                                .dosage(1)
                                .frequency(2)
                                .timings(List.of(MedicationTiming.AFTER_BREAKFAST, MedicationTiming.AFTER_DINNER))
                                .durationDays(30)
                                .totalCount(60)
                                .entpName("바이엘코리아(주)")
                                .matchedByEmbedding(false)
                                .build(),
                        // 임베딩 매칭 예시 (useEmbedding=true일 때)
                        ScanResponseDTO.ScannedMedication.builder()
                                .name("메트포민500mg")  // OCR에서 오인식된 이름
                                .drugItemSeq(useEmbedding ? "200808001" : null)
                                .dosage(1)
                                .frequency(2)
                                .timings(List.of(MedicationTiming.AFTER_BREAKFAST, MedicationTiming.AFTER_DINNER))
                                .durationDays(30)
                                .totalCount(60)
                                .entpName(useEmbedding ? "대웅제약(주)" : null)
                                .matchedByEmbedding(useEmbedding ? true : null)
                                .build()
                ))
                .notes(useEmbedding ? "임베딩 모드: OCR 오인식 시 유사 약물로 자동 매칭 (Mock)" : null)
                .build();
    }
}

package com.myyak.service.scanService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.domain.DrugInfo;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.repository.DrugInfoRepository;
import com.myyak.service.drugSearchService.DrugSearchService;
import com.myyak.web.dto.ScanDTO.ScanResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "scan.strategy", havingValue = "vision-only", matchIfMissing = true)
public class ScanServiceImpl implements ScanService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final DrugInfoRepository drugInfoRepository;
    private final DrugSearchService drugSearchService;

    @Value("${ai.vision-provider:openai}")
    private String visionProvider;

    @Value("${ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${ai.openai.model:gpt-4o-mini}")
    private String openaiModel;

    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.gemini.model:gemini-3-pro-preview}")
    private String geminiModel;

    @Value("${app.debug.save-scan-images:false}")
    private boolean saveScanImages;

    @Value("${app.debug.image-dir:#{systemProperties['java.io.tmpdir']}/myyak/scan_debug}")
    private String debugImageDir;

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final String VISION_PROMPT = """
            당신은 한국 처방전/약봉투 이미지를 분석하는 전문 약사입니다.
            당신은 한국에서 처방되는 모든 의약품에 대한 깊은 지식을 갖추고 있습니다.

            이미지에서 의약품 정보와 처방전 메타정보를 추출하여 JSON 형식으로 반환하세요.

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
              "patientName": "홍길동",
              "hospitalName": "서울내과의원",
              "diagnosis": "급성 상기도감염",
              "durationDays": 7,
              "medications": [
                {
                  "name": "메드론정",
                  "dosage": 1,
                  "frequency": 2,
                  "timings": ["MORNING", "EVENING"],
                  "durationDays": 7,
                  "totalCount": 14
                }
              ],
              "notes": null
            }

            ## 처방전 메타정보 필드 (최상위 레벨):
            - patientName: 환자명 (처방전에 기재된 이름, 없으면 null)
            - hospitalName: 병원/약국명 (없으면 null)
            - diagnosis: 진단명, 질환명, 증상 (처방전에 기재되어 있거나 처방 약물로 추론 가능하면 작성, 없으면 null)
              - 예시: "급성 상기도감염", "고혈압", "당뇨", "위염", "감기" 등
              - 진단명이 직접 기재되어 있지 않아도, 처방된 약물 조합으로 추론 가능하면 작성
            - durationDays: 총 복용 기간 (일). 약물별 durationDays 중 가장 긴 기간 또는 처방전에 명시된 기간

            ## 약물 필드 설명:
            - name: 순수 약품명만 (예: "메드론정", "아스피린정", "타이레놀정")
            - dosage: 1회 복용량 (알 개수, 기본값 1)
            - frequency: 하루 복용 횟수 (기본값 1)
            - timings: 복용 시간대 배열
            - durationDays: 해당 약물의 복용 일수 (기본값 7)
            - totalCount: 총 약 개수 (dosage × frequency × durationDays)

            ## 약품명(name) 작성 규칙 (매우 중요):
            - 순수 약품명만 추출하세요
            - 제외할 것: 성분명, 보관방법, 부가설명, 괄호 안 내용
            - 좋은 예: "메드론정", "에이스타정", "아스피린프로텍트정100mg"
            - 나쁜 예: "메드론정 (안전보관)", "에이스타정(위험보관)", "메드론정(메틸프레드니솔론)"
            - 용량(mg, ml 등)은 약품명에 붙어있으면 포함 가능

            ## 🔍 약품명 추론 전략 (핵심):
            이미지가 구겨진 약봉투일 수 있습니다. 텍스트가 일부 가려져 있거나, 접혀 있거나, 왜곡되어 있어도
            일반적인 의약품 명칭과 처방전 맥락을 고려해서 올바른 단어로 자동 보정해서 추출하세요.

            약품명이 불명확하거나 일부만 보이는 경우, 다음 정보를 종합하여 정확한 약품명을 추론하세요:

            1. **효능/효과 정보 활용**: 처방전에 적힌 효능, 적응증, 질병명을 참고
               - "소화제" → 베아제정, 훼스탈플러스정, 가스모틴정 등
               - "진통제", "해열" → 타이레놀정, 이지엔6이브연질캡슐 등
               - "항생제" → 아목시실린, 세프트리악손 등
               - "위장약" → 란소프라졸, 에스오메프라졸 등

            2. **함께 처방된 약물 조합 분석**: 흔히 같이 처방되는 약물 패턴 활용
               - 감기약 세트: 항히스타민제 + 진통제 + 기침약
               - 위장약 세트: 제산제 + 위점막보호제 + 소화효소제
               - 고혈압 세트: ACE억제제 + 이뇨제 + 칼슘차단제

            3. **흔한 오타/오인식 패턴 교정**:
               - "메드론" → "메드론정" (스테로이드)
               - "타이래놀" → "타이레놀정"
               - "아스피린프로텍트" → "아스피린프로텍트정100mg"
               - 글자가 잘려서 보이면 완전한 약품명으로 복원

            4. **한국에서 자주 처방되는 약물 지식 활용**:
               - 유사한 발음/철자의 약물 중 처방 빈도가 높은 것 선택
               - 제형(정, 캡슐, 시럽 등)이 맞는지 확인

            5. **맥락적 단서 활용**:
               - 병원/약국명으로 진료과 추정 (내과, 이비인후과 등)
               - 환자 연령대 정보가 있다면 소아용/성인용 구분

            ## timings 값 (복용 시간대):
            - MORNING: 아침
            - AFTERNOON: 점심
            - EVENING: 저녁
            - AS_NEEDED: 필요시

            ## 복용 시간 해석 규칙:
            - "1일 1회" → frequency: 1, timings: ["MORNING"]
            - "1일 2회" → frequency: 2, timings: ["MORNING", "EVENING"]
            - "1일 3회" → frequency: 3, timings: ["MORNING", "AFTERNOON", "EVENING"]
            - 시간대 정보 없으면 기본값: ["MORNING"]

            ## 주의사항:
            1. 반드시 유효한 JSON만 출력 (마크다운 코드블록 없이)
            2. 읽을 수 없는 필드는 기본값 사용
            3. 약품명이 부분적으로만 보여도 위 추론 전략을 활용해 정확한 약품명 입력
            4. 한국어 약품명, 영어 약품명 모두 인식
            5. 추론한 경우에도 가장 가능성 높은 정확한 약품명을 name에 입력
            """;

    @Override
    public ScanResponseDTO.ScanResult scanPrescription(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new GeneralException(ErrorStatus.SCAN_IMAGE_REQUIRED);
        }

        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }

        // 디버깅용 이미지 저장
        saveDebugImage(image);

        // Vision 프로바이더 선택
        boolean useGemini = "gemini".equalsIgnoreCase(visionProvider);
        log.info("Vision 프로바이더: {}", useGemini ? "Gemini" : "OpenAI");

        if (useGemini) {
            return processWithGemini(image, contentType);
        } else {
            return processWithOpenAI(image, contentType);
        }
    }

    /**
     * OpenAI Vision API로 처리
     */
    private ScanResponseDTO.ScanResult processWithOpenAI(MultipartFile image, String contentType) {
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            log.warn("OpenAI API key is not configured. Returning mock data.");
            return createMockResponse();
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

            return parseOpenAIResponse(response);
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI Vision API error: ", e);
            throw new GeneralException(ErrorStatus.VISION_API_ERROR);
        }
    }

    /**
     * Gemini Vision API로 처리
     */
    private ScanResponseDTO.ScanResult processWithGemini(MultipartFile image, String contentType) {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            log.warn("Gemini API key is not configured. Returning mock data.");
            return createMockResponse();
        }

        try {
            byte[] imageBytes = image.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            Map<String, Object> requestBody = buildGeminiRequest(base64Image, contentType);

            String geminiUrl = GEMINI_API_URL + geminiModel + ":generateContent?key=" + geminiApiKey;

            long startTime = System.currentTimeMillis();

            String response = webClient.post()
                    .uri(geminiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Gemini API 응답 시간: {}ms ({}초)", elapsed, elapsed / 1000.0);

            return parseGeminiResponse(response);
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gemini Vision API error: ", e);
            throw new GeneralException(ErrorStatus.VISION_API_ERROR);
        }
    }

    private Map<String, Object> buildOpenAIRequest(String base64Image, String mimeType) {
        String imageUrl = "data:" + mimeType + ";base64," + base64Image;

        Map<String, Object> imageUrlObj = new HashMap<>();
        imageUrlObj.put("url", imageUrl);

        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        imageContent.put("image_url", imageUrlObj);

        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", VISION_PROMPT);

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", List.of(textContent, imageContent));

        Map<String, Object> request = new HashMap<>();
        request.put("model", openaiModel);
        request.put("messages", List.of(userMessage));
        request.put("max_tokens", 2000);

        return request;
    }

    private Map<String, Object> buildGeminiRequest(String base64Image, String mimeType) {
        Map<String, Object> inlineData = new HashMap<>();
        inlineData.put("mime_type", mimeType);
        inlineData.put("data", base64Image);

        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("inline_data", inlineData);

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", VISION_PROMPT);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(textPart, imagePart));

        Map<String, Object> request = new HashMap<>();
        request.put("contents", List.of(content));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("maxOutputTokens", 8192);
        generationConfig.put("temperature", 0.4);
        request.put("generationConfig", generationConfig);

        return request;
    }

    private ScanResponseDTO.ScanResult parseGeminiResponse(String response) {
        try {
            log.debug("Gemini 원본 응답 (앞 500자): {}", response != null && response.length() > 500 ? response.substring(0, 500) + "..." : response);

            JsonNode root = objectMapper.readTree(response);

            JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                String errorMessage = error.path("message").asText("알 수 없는 오류");
                log.error("Gemini API 에러: {}", errorMessage);
                return createLowConfidenceResponse("AI API 오류: " + errorMessage);
            }

            JsonNode candidates = root.path("candidates");

            if (candidates.isEmpty() || !candidates.isArray() || candidates.size() == 0) {
                log.error("Gemini 응답에 candidates가 없음: {}", response);
                return createLowConfidenceResponse("AI 응답을 받지 못했습니다.");
            }

            JsonNode firstCandidate = candidates.get(0);
            if (firstCandidate == null) {
                log.error("Gemini candidates[0]이 null: {}", response);
                return createLowConfidenceResponse("AI 응답 형식 오류");
            }

            JsonNode content = firstCandidate.path("content");
            if (content.isMissingNode()) {
                log.error("Gemini content가 없음: {}", response);
                return createLowConfidenceResponse("AI 응답 형식 오류");
            }

            JsonNode parts = content.path("parts");
            if (parts.isMissingNode() || !parts.isArray() || parts.size() == 0) {
                log.error("Gemini parts가 없거나 비어있음: {}", response);
                return createLowConfidenceResponse("AI 응답 형식 오류");
            }

            JsonNode firstPart = parts.get(0);
            if (firstPart == null) {
                log.error("Gemini parts[0]이 null: {}", response);
                return createLowConfidenceResponse("AI 응답 형식 오류");
            }

            String text = firstPart.path("text").asText();
            if (text == null || text.isEmpty()) {
                log.error("Gemini text가 비어있음: {}", response);
                return createLowConfidenceResponse("AI 응답이 비어있습니다.");
            }

            log.info("Gemini Vision 응답: {}", text);

            return parseVisionResponse(text);
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: ", e);
            return createLowConfidenceResponse("응답 파싱에 실패했습니다.");
        }
    }

    private ScanResponseDTO.ScanResult parseOpenAIResponse(String response) {
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

            return parseVisionResponse(text);
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response: ", e);
            return createLowConfidenceResponse("응답 파싱에 실패했습니다.");
        }
    }

    /**
     * Vision API 공통 응답 파싱
     */
    private ScanResponseDTO.ScanResult parseVisionResponse(String text) {
        try {
            String jsonText = extractJsonFromText(text);
            JsonNode resultJson = objectMapper.readTree(jsonText);

            boolean success = resultJson.path("success").asBoolean(false);
            String confidence = resultJson.path("confidence").asText("low");
            String notes = resultJson.path("notes").isNull() ? null : resultJson.path("notes").asText();

            // 처방전 메타정보 추출
            String patientName = resultJson.path("patientName").isNull() ? null : resultJson.path("patientName").asText();
            String hospitalName = resultJson.path("hospitalName").isNull() ? null : resultJson.path("hospitalName").asText();
            String diagnosis = resultJson.path("diagnosis").isNull() ? null : resultJson.path("diagnosis").asText();
            Integer totalDurationDays = resultJson.path("durationDays").isNull() ? null : resultJson.path("durationDays").asInt();

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

                    // DrugInfo가 매칭되면 추가 정보 설정
                    if (matchedDrug != null) {
                        builder.name(extractPureDrugName(matchedDrug.getItemName()))
                               .drugItemSeq(matchedDrug.getItemSeq())
                               .ingredient(resolveIngredient(matchedDrug.getItemName(), matchedDrug.getIngredientName()))
                               .efficacy(matchedDrug.getEfficacy())
                               .imageUrl(matchedDrug.getImageUrl())
                               .entpName(matchedDrug.getEntpName());
                        log.info("약물 '{}' → DB 매칭: '{}'", name, matchedDrug.getItemName());
                    }

                    // DB 매칭 실패 시 → 편집거리 검색으로 OCR 오타 보정
                    if (matchedDrug == null && name != null && !name.isBlank()) {
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
                    .patientName(patientName)
                    .hospitalName(hospitalName)
                    .diagnosis(diagnosis)
                    .durationDays(totalDurationDays)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Vision response: ", e);
            return createLowConfidenceResponse("응답 파싱에 실패했습니다.");
        }
    }

    private DrugInfo findDrugInfo(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        Optional<DrugInfo> exact = drugInfoRepository.findByItemName(name);
        if (exact.isPresent()) {
            return exact.get();
        }

        List<DrugInfo> matches = drugInfoRepository.findByItemNameContaining(name);
        if (!matches.isEmpty()) {
            return matches.get(0);
        }

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

    private String extractPureDrugName(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return itemName;
        }
        return itemName.replaceAll("\\([^)]*\\)", "").trim();
    }

    private String extractIngredientFromParentheses(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\(([^)]+)\\)");
        java.util.regex.Matcher matcher = pattern.matcher(itemName);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

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

    private void saveDebugImage(MultipartFile image) {
        if (!saveScanImages) {
            log.debug("[DEBUG] 스캔 이미지 저장이 비활성화되어 있습니다.");
            return;
        }

        try {
            Path debugDir = Paths.get(debugImageDir);
            if (!Files.exists(debugDir)) {
                Files.createDirectories(debugDir);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            String originalFilename = image.getOriginalFilename();
            String extension = "png";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
            }
            String filename = String.format("scan_%s.%s", timestamp, extension);

            Path filePath = debugDir.resolve(filename);
            Files.write(filePath, image.getBytes());

            log.info("[DEBUG] 스캔 이미지 저장: {} ({}x? bytes: {})",
                    filePath.toAbsolutePath(), image.getContentType(), image.getSize());
        } catch (IOException e) {
            log.warn("[DEBUG] 이미지 저장 실패: {}", e.getMessage());
        }
    }

    private ScanResponseDTO.ScanResult createMockResponse() {
        return ScanResponseDTO.ScanResult.builder()
                .success(true)
                .confidence("high")
                .medications(List.of(
                        ScanResponseDTO.ScannedMedication.builder()
                                .name("아스피린프로텍트100mg")
                                .drugItemSeq("200003933")
                                .dosage(1)
                                .frequency(2)
                                .timings(List.of(MedicationTiming.MORNING, MedicationTiming.EVENING))
                                .durationDays(30)
                                .totalCount(60)
                                .entpName("바이엘코리아(주)")
                                .build(),
                        ScanResponseDTO.ScannedMedication.builder()
                                .name("메트포민500mg")
                                .dosage(1)
                                .frequency(2)
                                .timings(List.of(MedicationTiming.MORNING, MedicationTiming.EVENING))
                                .durationDays(30)
                                .totalCount(60)
                                .build()
                ))
                .notes(null)
                .build();
    }
}

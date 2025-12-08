package com.myyak.service.scanService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.domain.DrugInfo;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.repository.DrugInfoRepository;
import com.myyak.web.dto.ScanDTO.ScanResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanServiceImpl implements ScanService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final DrugInfoRepository drugInfoRepository;

    @Value("${ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${ai.openai.model:gpt-4o-mini}")
    private String openaiModel;

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

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
                  "name": "약품명 전체 (용량 포함)",
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
            - name: 약품명과 용량 (예: "아스피린프로텍트100mg")
            - dosage: 1회 복용량 (알 개수, 기본값 1)
            - frequency: 하루 복용 횟수 (기본값 1)
            - timings: 복용 시간대 배열
            - durationDays: 총 복용 일수 (기본값 7)
            - totalCount: 총 약 개수 (dosage × frequency × durationDays)

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
        if (image == null || image.isEmpty()) {
            throw new GeneralException(ErrorStatus.SCAN_IMAGE_REQUIRED);
        }

        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }

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

                    // DrugInfo가 매칭되면 추가 정보 설정
                    if (matchedDrug != null) {
                        builder.drugItemSeq(matchedDrug.getItemSeq())
                               .efficacy(matchedDrug.getEfficacy())
                               .imageUrl(matchedDrug.getImageUrl())
                               .entpName(matchedDrug.getEntpName());
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

    private ScanResponseDTO.ScanResult createLowConfidenceResponse(String notes) {
        return ScanResponseDTO.ScanResult.builder()
                .success(false)
                .confidence("low")
                .medications(List.of())
                .notes(notes)
                .build();
    }

    private ScanResponseDTO.ScanResult createMockResponse() {
        return ScanResponseDTO.ScanResult.builder()
                .success(true)
                .confidence("high")
                .medications(List.of(
                        ScanResponseDTO.ScannedMedication.builder()
                                .name("아스피린프로텍트100mg")
                                .dosage(1)
                                .frequency(2)
                                .timings(List.of(MedicationTiming.AFTER_BREAKFAST, MedicationTiming.AFTER_DINNER))
                                .durationDays(30)
                                .totalCount(60)
                                .build(),
                        ScanResponseDTO.ScannedMedication.builder()
                                .name("메트포르민500mg")
                                .dosage(1)
                                .frequency(2)
                                .timings(List.of(MedicationTiming.AFTER_BREAKFAST, MedicationTiming.AFTER_DINNER))
                                .durationDays(30)
                                .totalCount(60)
                                .build()
                ))
                .notes(null)
                .build();
    }
}

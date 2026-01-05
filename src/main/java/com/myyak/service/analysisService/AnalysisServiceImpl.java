package com.myyak.service.analysisService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.converter.AnalysisConverter;
import com.myyak.domain.*;
import com.myyak.repository.*;
import com.myyak.service.llm.LlmClient;
import com.myyak.web.dto.AnalysisDTO.AnalysisResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * AI 약물 분석 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AnalysisServiceImpl implements AnalysisService {

    private final UserRepository userRepository;
    private final UserMedicationRepository userMedicationRepository;
    private final DrugInteractionRepository drugInteractionRepository;
    private final DrugFoodInteractionRepository drugFoodInteractionRepository;
    private final AnalysisReportRepository analysisReportRepository;
    private final UserAnalysisQuotaRepository userAnalysisQuotaRepository;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            당신은 복약 정보 요약 도우미입니다.

            역할:
            - 사용자가 복용 중인 약물들의 작용 맥락을 설명합니다
            - 음식/영양제와의 병용 시 주의가 필요한 이유를 설명합니다
            - 모바일 앱 UI에서 바로 사용할 수 있는 JSON을 생성합니다

            규칙:
            1. 의학적 판단을 하지 않습니다
            2. "위험합니다", "피하세요", "금지" 같은 표현을 사용하지 않습니다
            3. 복용 지시나 권고를 하지 않습니다
            4. 일반인이 이해할 수 있는 쉬운 표현을 사용합니다
            5. 비유를 활용하여 이해를 돕습니다

            출력 형식:
            반드시 아래 JSON 스키마에 맞춰 응답합니다. JSON만 출력하고 다른 텍스트는 포함하지 않습니다.

            {
              "mechanismGroups": [
                {
                  "categoryName": "카테고리명 (예: 고혈압 조절)",
                  "categoryIcon": "관련 이모지 1개 (예: ❤️, 💊, 🩸, 🧠, 🦴 등)",
                  "description": "2~3문장 설명",
                  "analogy": "비유 1줄",
                  "medicationCount": 숫자,
                  "medications": [
                    {
                      "name": "약물명",
                      "ingredientName": "성분명"
                    }
                  ]
                }
              ],
              "foodInteractions": [
                {
                  "foodName": "음식명",
                  "foodIcon": "이모지",
                  "interactionLevel": "HIGH/MEDIUM/LOW",
                  "affectedMedicationCount": 숫자,
                  "summaryReason": "요약 이유 1줄",
                  "details": [
                    {
                      "medicationName": "약물명",
                      "reason": "상세 이유"
                    }
                  ]
                }
              ]
            }
            """;

    @Override
    public AnalysisResponseDTO.AnalysisResult requestAnalysis(Long userId) {
        User user = findUserById(userId);

        // TODO: 유료 결제 도입 후 쿼터 제한 활성화
        // // 1. 쿼터 확인 및 리셋 처리
        // UserAnalysisQuota quota = getOrCreateQuota(user);
        // checkAndResetQuotaIfNeeded(quota);
        //
        // if (!quota.canAnalyze()) {
        //     throw new GeneralException(ErrorStatus.ANALYSIS_QUOTA_EXCEEDED);
        // }

        // 1. 사용자 복용 약물 조회
        List<UserMedication> medications = userMedicationRepository.findByUserWithDrugInfo(user);
        if (medications.isEmpty()) {
            throw new GeneralException(ErrorStatus.ANALYSIS_NO_MEDICATIONS);
        }

        // 2. 약물 코드 및 성분명 추출
        List<String> itemSeqs = medications.stream()
                .filter(m -> m.getDrugInfo() != null)
                .map(m -> m.getDrugInfo().getItemSeq())
                .toList();

        List<String> ingredientNames = medications.stream()
                .filter(m -> m.getDrugInfo() != null && m.getDrugInfo().getIngredientKr() != null)
                .map(m -> m.getDrugInfo().getIngredientKr())
                .distinct()
                .toList();

        // 3. 약-약 상호작용 조회
        List<DrugInteraction> drugInteractions = itemSeqs.isEmpty() ?
                List.of() : drugInteractionRepository.findByDrugItemSeqsIn(itemSeqs);

        // 4. 약-음식 상호작용 조회
        List<DrugFoodInteraction> foodInteractions = (itemSeqs.isEmpty() && ingredientNames.isEmpty()) ?
                List.of() : drugFoodInteractionRepository.findByDrugItemSeqsOrIngredientNames(itemSeqs, ingredientNames);

        // 5. LLM 입력 JSON 생성
        String userPrompt = buildUserPrompt(medications, drugInteractions, foodInteractions);
        log.info("[Analysis] LLM 입력 프롬프트 길이: {}", userPrompt.length());

        // 6. LLM 호출
        String llmResponse;
        try {
            llmResponse = llmClient.generate(SYSTEM_PROMPT, userPrompt);
            log.info("[Analysis] LLM 응답 길이: {}", llmResponse.length());
        } catch (Exception e) {
            log.error("[Analysis] LLM 호출 실패: ", e);
            throw new GeneralException(ErrorStatus.ANALYSIS_LLM_ERROR);
        }

        // 7. JSON 추출 및 검증
        String cleanedResponse = extractJsonFromResponse(llmResponse);

        // 8. 레포트 저장
        String medicationSnapshot = buildMedicationSnapshot(medications);
        int mechanismGroupCount = countMechanismGroups(cleanedResponse);
        int foodInteractionCount = countFoodInteractions(cleanedResponse);

        AnalysisReport report = AnalysisReport.builder()
                .user(user)
                .analysisDate(LocalDateTime.now())
                .mechanismGroupCount(mechanismGroupCount)
                .foodInteractionCount(foodInteractionCount)
                .medicationSnapshot(medicationSnapshot)
                .llmResponse(cleanedResponse)
                .build();

        analysisReportRepository.save(report);

        // TODO: 유료 결제 도입 후 쿼터 사용량 증가 활성화
        // quota.incrementUsedCount();

        log.info("[Analysis] 분석 완료 - userId: {}, reportId: {}, mechanisms: {}, foods: {}",
                userId, report.getId(), mechanismGroupCount, foodInteractionCount);

        return AnalysisConverter.toAnalysisResult(report, null);
    }

    @Override
    @Transactional(readOnly = true)
    public AnalysisResponseDTO.ReportList getReportList(Long userId) {
        User user = findUserById(userId);
        List<AnalysisReport> reports = analysisReportRepository.findByUserOrderByAnalysisDateDesc(user);
        return AnalysisConverter.toReportList(reports);
    }

    @Override
    @Transactional(readOnly = true)
    public AnalysisResponseDTO.AnalysisResult getReportDetail(Long userId, Long reportId) {
        User user = findUserById(userId);

        AnalysisReport report = analysisReportRepository.findByIdAndUser(reportId, user)
                .orElseThrow(() -> new GeneralException(ErrorStatus.ANALYSIS_REPORT_NOT_FOUND));

        UserAnalysisQuota quota = userAnalysisQuotaRepository.findByUser(user).orElse(null);

        return AnalysisConverter.toAnalysisResult(report, quota);
    }

    @Override
    public void deleteReport(Long userId, Long reportId) {
        User user = findUserById(userId);

        if (!analysisReportRepository.findByIdAndUser(reportId, user).isPresent()) {
            throw new GeneralException(ErrorStatus.ANALYSIS_REPORT_NOT_FOUND);
        }

        analysisReportRepository.deleteByIdAndUser(reportId, user);
        log.info("[Analysis] 레포트 삭제 - userId: {}, reportId: {}", userId, reportId);
    }

    @Override
    @Transactional(readOnly = true)
    public AnalysisResponseDTO.QuotaInfo getQuotaInfo(Long userId) {
        User user = findUserById(userId);
        UserAnalysisQuota quota = userAnalysisQuotaRepository.findByUser(user).orElse(null);
        return AnalysisConverter.toQuotaInfo(quota);
    }

    // ===== Private Methods =====

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));
    }

    private UserAnalysisQuota getOrCreateQuota(User user) {
        return userAnalysisQuotaRepository.findByUser(user)
                .orElseGet(() -> {
                    UserAnalysisQuota newQuota = UserAnalysisQuota.builder()
                            .user(user)
                            .monthlyLimit(3)
                            .usedCount(0)
                            .resetDate(getNextMonthFirstDay())
                            .build();
                    return userAnalysisQuotaRepository.save(newQuota);
                });
    }

    private void checkAndResetQuotaIfNeeded(UserAnalysisQuota quota) {
        LocalDate today = LocalDate.now();
        if (quota.needsReset(today)) {
            quota.resetMonthlyQuota(getNextMonthFirstDay());
            log.info("[Analysis] 쿼터 리셋 - userId: {}", quota.getUser().getId());
        }
    }

    private LocalDate getNextMonthFirstDay() {
        return LocalDate.now().plusMonths(1).withDayOfMonth(1);
    }

    private String buildUserPrompt(List<UserMedication> medications,
                                    List<DrugInteraction> drugInteractions,
                                    List<DrugFoodInteraction> foodInteractions) {
        Map<String, Object> input = new LinkedHashMap<>();

        // 사용자 복용 약물 목록
        List<Map<String, Object>> userMeds = medications.stream()
                .map(this::buildMedicationInfo)
                .toList();
        input.put("user_medications", userMeds);

        // 약-약 상호작용
        List<Map<String, Object>> drugIntList = drugInteractions.stream()
                .map(this::buildDrugInteractionInfo)
                .toList();
        input.put("drug_interactions", drugIntList);

        // 약-음식 상호작용
        List<Map<String, Object>> foodIntList = foodInteractions.stream()
                .map(this::buildFoodInteractionInfo)
                .toList();
        input.put("food_interactions", foodIntList);

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(input);
        } catch (JsonProcessingException e) {
            log.error("[Analysis] 프롬프트 JSON 생성 실패: ", e);
            throw new GeneralException(ErrorStatus.ANALYSIS_LLM_ERROR);
        }
    }

    private Map<String, Object> buildMedicationInfo(UserMedication medication) {
        Map<String, Object> info = new LinkedHashMap<>();
        DrugInfo drug = medication.getDrugInfo();

        if (drug != null) {
            info.put("item_seq", drug.getItemSeq());
            info.put("display_name", drug.getDisplayName() != null ? drug.getDisplayName() : drug.getItemName());
            info.put("ingredient_kr", drug.getIngredientKr());
            info.put("efficacy_summary", truncateText(drug.getEfficacy(), 100));
        } else {
            info.put("item_seq", null);
            info.put("display_name", medication.getCustomDrugName());
            info.put("ingredient_kr", null);
            info.put("efficacy_summary", null);
        }

        return info;
    }

    private Map<String, Object> buildDrugInteractionInfo(DrugInteraction interaction) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("drug_a", interaction.getDrugAItemSeq());
        info.put("drug_b", interaction.getDrugBItemSeq());
        info.put("interaction_data", interaction.getInteractionData());
        return info;
    }

    private Map<String, Object> buildFoodInteractionInfo(DrugFoodInteraction interaction) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("drug_item_seq", interaction.getDrugItemSeq());
        info.put("ingredient_name", interaction.getIngredientName());
        info.put("food_name", interaction.getFoodName());
        info.put("food_icon", interaction.getFood() != null ? interaction.getFood().getIconEmoji() : null);
        info.put("interaction_level", interaction.getInteractionLevel() != null ?
                interaction.getInteractionLevel().name() : null);
        info.put("interaction_data", interaction.getInteractionData());
        return info;
    }

    private String buildMedicationSnapshot(List<UserMedication> medications) {
        List<Map<String, Object>> snapshot = medications.stream()
                .map(m -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    DrugInfo drug = m.getDrugInfo();
                    if (drug != null) {
                        info.put("itemSeq", drug.getItemSeq());
                        info.put("displayName", drug.getDisplayName() != null ? drug.getDisplayName() : drug.getItemName());
                        info.put("ingredientKr", drug.getIngredientKr());
                    } else {
                        info.put("itemSeq", null);
                        info.put("displayName", m.getCustomDrugName());
                        info.put("ingredientKr", null);
                    }
                    return info;
                })
                .toList();

        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("[Analysis] 스냅샷 JSON 생성 실패: ", e);
            return "[]";
        }
    }

    private String extractJsonFromResponse(String response) {
        if (response == null || response.isBlank()) {
            return "{}";
        }

        // JSON 블록 추출 (```json ... ``` 또는 { ... })
        String trimmed = response.trim();

        // 코드 블록 제거
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }

        // JSON 객체 추출
        int start = trimmed.indexOf("{");
        int end = trimmed.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        return trimmed;
    }

    @SuppressWarnings("unchecked")
    private int countMechanismGroups(String llmResponse) {
        try {
            Map<String, Object> response = objectMapper.readValue(llmResponse, Map.class);
            Object groups = response.get("mechanismGroups");
            if (groups instanceof List) {
                return ((List<?>) groups).size();
            }
        } catch (Exception e) {
            log.warn("[Analysis] mechanismGroups 카운트 실패: ", e);
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private int countFoodInteractions(String llmResponse) {
        try {
            Map<String, Object> response = objectMapper.readValue(llmResponse, Map.class);
            Object foods = response.get("foodInteractions");
            if (foods instanceof List) {
                return ((List<?>) foods).size();
            }
        } catch (Exception e) {
            log.warn("[Analysis] foodInteractions 카운트 실패: ", e);
        }
        return 0;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}

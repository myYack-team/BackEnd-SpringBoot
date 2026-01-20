package com.myyak.service.analysisService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.converter.AnalysisConverter;
import com.myyak.domain.*;
import com.myyak.domain.enums.IntakeStatus;
import com.myyak.repository.*;
import com.myyak.service.llm.LlmClient;
import com.myyak.web.dto.AnalysisDTO.AnalysisResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final UserSupplementRepository userSupplementRepository;
    private final DrugInteractionRepository drugInteractionRepository;
    private final DrugFoodInteractionRepository drugFoodInteractionRepository;
    private final AnalysisReportRepository analysisReportRepository;
    private final UserAnalysisQuotaRepository userAnalysisQuotaRepository;
    private final IntakeRepository intakeRepository;
    private final HealthNoteRepository healthNoteRepository;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    private static final int PATTERN_ANALYSIS_DAYS = 30;  // 패턴 분석 기간 (30일)

    private static final String SYSTEM_PROMPT = """
            당신은 복약 정보 요약 도우미입니다.

            역할:
            - 사용자가 복용 중인 약물들의 작용 맥락을 설명합니다
            - 음식/영양제와의 병용 시 주의가 필요한 이유를 설명합니다
            - 복용 중인 약물에 도움이 되는 음식과 생활 습관을 제안합니다
            - 모바일 앱 UI에서 바로 사용할 수 있는 JSON을 생성합니다

            규칙:
            1. 의학적 판단을 하지 않습니다
            2. "위험합니다", "피하세요", "금지" 같은 표현을 사용하지 않습니다
            3. 복용 지시나 권고를 하지 않습니다
            4. 일반인이 이해할 수 있는 쉬운 표현을 사용합니다
            5. 비유를 활용하여 이해를 돕습니다
            6. 생활 팁은 학술적 근거가 있는 내용만 포함합니다 (출처 필수)
            7. 영양제 상호작용은 사용자가 복용 중인 영양제만 분석합니다

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
              ],
              "foodSuggestions": [
                {
                  "foodName": "음식명",
                  "foodIcon": "이모지",
                  "reason": "추천 이유 1줄",
                  "tip": "섭취 팁 (선택, 없으면 null)",
                  "relatedMedications": [
                    {
                      "name": "약물명",
                      "detail": "추가 설명 (선택, 없으면 null)"
                    }
                  ]
                }
              ],
              "supplementInteractions": [
                {
                  "supplementName": "영양제명",
                  "supplementTag": "영양제 태그 (VITAMIN_A, VITAMIN_B, VITAMIN_C, VITAMIN_D, VITAMIN_E, OMEGA_3, MAGNESIUM, CALCIUM, IRON, ZINC, ARGININE, COLLAGEN, PROBIOTICS, LUTEIN, COENZYME_Q10, OTHER 중 하나)",
                  "interactionLevel": "GOOD/TIMING/CAUTION (GOOD: 좋은 궁합, TIMING: 복용 시간 조절 필요, CAUTION: 주의 필요)",
                  "summaryReason": "요약 이유 1줄",
                  "source": "출처 (선택, 없으면 null)",
                  "details": [
                    {
                      "medicationName": "약물명",
                      "reason": "상세 이유"
                    }
                  ]
                }
              ],
              "lifestyleTips": [
                {
                  "category": "카테고리 코드 (EXERCISE, SLEEP, DIET, HYDRATION, STRESS, POSTURE 중 하나)",
                  "categoryIcon": "카테고리 이모지 (🏃, 😴, 🥗, 💧, 🧘, 🪑 등)",
                  "categoryLabel": "카테고리 한글명 (운동, 수면, 식이, 수분 섭취, 스트레스 관리, 자세 등)",
                  "title": "팁 제목 (짧게)",
                  "tip": "팁 내용 1~2줄",
                  "detailedExplanation": "상세 설명 2~3줄",
                  "source": "출처 (필수, 예: 대한고혈압학회 가이드라인)",
                  "relatedMedications": [
                    {
                      "name": "약물명",
                      "detail": "추가 설명 (선택, 없으면 null)"
                    }
                  ]
                }
              ]
            }
            """;

    private static final String PATTERN_ANALYSIS_SYSTEM_PROMPT = """
            당신은 복약 습관 분석 도우미입니다.

            역할:
            - 사용자의 30일간 복약 기록과 건강 메모를 분석합니다
            - 복약 패턴을 파악하고 개선을 위한 인사이트를 제공합니다
            - 컨디션 점수와 복약률의 상관관계를 분석합니다
            - 격려와 응원의 메시지를 전달합니다

            규칙:
            1. 의학적 판단을 하지 않습니다
            2. "위험합니다", "경고", "심각합니다" 같은 표현을 사용하지 않습니다
            3. 복용 지시나 의학적 권고를 하지 않습니다
            4. 긍정적이고 격려하는 톤을 유지합니다
            5. 구체적이고 실천 가능한 습관 개선 제안만 합니다
            6. 사용자의 노력을 인정하고 칭찬합니다

            출력 형식:
            반드시 아래 JSON 스키마에 맞춰 응답합니다. JSON만 출력하고 다른 텍스트는 포함하지 않습니다.

            {
              "adherenceAnalysis": {
                "overallRate": 0~100 사이 숫자 (전체 복약률),
                "weekdayPattern": {
                  "mondayRate": 숫자, "tuesdayRate": 숫자, "wednesdayRate": 숫자,
                  "thursdayRate": 숫자, "fridayRate": 숫자, "saturdayRate": 숫자, "sundayRate": 숫자,
                  "bestDay": "가장 복약률 높은 요일 한글 (예: 월요일)",
                  "worstDay": "가장 복약률 낮은 요일 한글 (예: 토요일)"
                },
                "timingPattern": {
                  "morningRate": 숫자, "lunchRate": 숫자, "dinnerRate": 숫자, "bedtimeRate": 숫자,
                  "bestTiming": "가장 복약률 높은 시간대 한글 (예: 아침)",
                  "worstTiming": "가장 복약률 낮은 시간대 한글 (예: 점심)"
                },
                "missedDays": 숫자 (복용 누락 일수),
                "perfectDays": 숫자 (완벽 복용 일수)
              },
              "patterns": [
                {
                  "patternType": "POSITIVE/NEGATIVE/NEUTRAL",
                  "patternIcon": "이모지 1개",
                  "title": "패턴 제목 (짧게)",
                  "description": "패턴 설명 1~2줄",
                  "suggestion": "개선 제안 (선택, POSITIVE면 null)"
                }
              ],
              "insights": [
                {
                  "insightType": "CONDITION_CORRELATION/HABIT_SUGGESTION/ACHIEVEMENT",
                  "insightIcon": "이모지 1개",
                  "title": "인사이트 제목",
                  "description": "인사이트 설명 1~2줄",
                  "actionItem": "실천 항목 (선택, 없으면 null)"
                }
              ],
              "summary": {
                "overallAssessment": "전반적인 평가 1~2문장",
                "positivePoint": "긍정적인 점 1문장",
                "improvementPoint": "개선이 필요한 점 1문장 (부정적 표현 금지)",
                "encouragement": "격려 메시지 1문장"
              },
              "events": [
                {
                  "date": "YYYY-MM-DD",
                  "eventType": "CONDITION_CHANGE/ADHERENCE_CHANGE/STREAK",
                  "eventIcon": "이모지 1개",
                  "title": "이벤트 제목",
                  "description": "이벤트 설명"
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

        // 5. 사용자 복용 영양제 조회
        List<UserSupplement> userSupplements = userSupplementRepository.findByUserWithSupplement(user);

        // 6. LLM 입력 JSON 생성
        String userPrompt = buildUserPrompt(medications, drugInteractions, foodInteractions, userSupplements);
        log.info("[Analysis] LLM 입력 프롬프트 길이: {}", userPrompt.length());

        // 7. LLM 호출
        String llmResponse;
        try {
            llmResponse = llmClient.generate(SYSTEM_PROMPT, userPrompt);
            log.info("[Analysis] LLM 응답 길이: {}", llmResponse.length());
        } catch (Exception e) {
            log.error("[Analysis] LLM 호출 실패: ", e);
            throw new GeneralException(ErrorStatus.ANALYSIS_LLM_ERROR);
        }

        // 8. JSON 추출 및 검증
        String cleanedResponse = extractJsonFromResponse(llmResponse);

        // 9. 패턴 분석 수행
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(PATTERN_ANALYSIS_DAYS - 1);
        String patternAnalysisJson = performPatternAnalysis(userId, startDate, endDate);

        // 10. 레포트 저장
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
                .patternAnalysis(patternAnalysisJson)
                .analysisStartDate(startDate)
                .analysisEndDate(endDate)
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

        // dailyConditions 조회 (분석 기간 내 건강 메모에서)
        List<AnalysisResponseDTO.DailyCondition> dailyConditions = buildDailyConditions(
                user, report.getAnalysisStartDate(), report.getAnalysisEndDate());

        return AnalysisConverter.toAnalysisResult(report, quota, dailyConditions);
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
                                    List<DrugFoodInteraction> foodInteractions,
                                    List<UserSupplement> userSupplements) {
        Map<String, Object> input = new LinkedHashMap<>();

        // 사용자 복용 약물 목록
        List<Map<String, Object>> userMeds = medications.stream()
                .map(this::buildMedicationInfo)
                .toList();
        input.put("user_medications", userMeds);

        // 사용자 복용 영양제 목록
        List<Map<String, Object>> userSupps = userSupplements.stream()
                .map(this::buildSupplementInfo)
                .toList();
        input.put("user_supplements", userSupps);

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

    private Map<String, Object> buildSupplementInfo(UserSupplement userSupplement) {
        Map<String, Object> info = new LinkedHashMap<>();
        Supplement supplement = userSupplement.getSupplement();

        if (supplement != null) {
            info.put("supplement_name", supplement.getName());
            info.put("supplement_tag", supplement.getTag() != null ? supplement.getTag().name() : null);
            info.put("supplement_tag_description", supplement.getTag() != null ? supplement.getTag().getDescription() : null);
            info.put("description", truncateText(supplement.getDescription(), 100));
        } else {
            info.put("supplement_name", null);
            info.put("supplement_tag", null);
            info.put("supplement_tag_description", null);
            info.put("description", null);
        }

        info.put("dosage", userSupplement.getDosage());
        info.put("frequency", userSupplement.getFrequency());

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

    // ===== Pattern Analysis Methods =====

    /**
     * 패턴 분석 수행
     * 30일간의 복약 기록과 건강 메모를 수집하여 LLM으로 분석
     */
    private String performPatternAnalysis(Long userId, LocalDate startDate, LocalDate endDate) {
        // 1. 30일간 복약 기록 조회
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
        List<Intake> intakes = intakeRepository.findAllByUserIdAndDateRangeWithDetails(
                userId, startDateTime, endDateTime);

        // 2. 30일간 건강 메모 조회
        List<HealthNote> healthNotes = healthNoteRepository.findByUserIdAndNoteDateBetween(
                userId, startDate, endDate);

        // 3. 데이터가 부족한 경우 빈 응답 반환
        if (intakes.isEmpty() && healthNotes.isEmpty()) {
            log.info("[PatternAnalysis] 분석할 데이터 부족 - userId: {}", userId);
            return null;
        }

        // 4. LLM 입력 JSON 생성
        String patternPrompt = buildPatternAnalysisPrompt(intakes, healthNotes, startDate, endDate);
        log.info("[PatternAnalysis] LLM 입력 프롬프트 길이: {}", patternPrompt.length());

        // 5. LLM 호출
        String patternResponse;
        try {
            patternResponse = llmClient.generate(PATTERN_ANALYSIS_SYSTEM_PROMPT, patternPrompt);
            log.info("[PatternAnalysis] LLM 응답 길이: {}", patternResponse.length());
        } catch (Exception e) {
            log.error("[PatternAnalysis] LLM 호출 실패: ", e);
            return null;  // 패턴 분석 실패해도 기본 분석은 진행
        }

        // 6. JSON 추출
        return extractJsonFromResponse(patternResponse);
    }

    /**
     * 패턴 분석용 프롬프트 생성
     */
    private String buildPatternAnalysisPrompt(List<Intake> intakes, List<HealthNote> healthNotes,
                                               LocalDate startDate, LocalDate endDate) {
        Map<String, Object> input = new LinkedHashMap<>();

        // 분석 기간
        input.put("analysis_period", Map.of(
                "start_date", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                "end_date", endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                "total_days", PATTERN_ANALYSIS_DAYS
        ));

        // 일별 데이터 구성
        List<Map<String, Object>> dailyData = buildDailyData(intakes, healthNotes, startDate, endDate);
        input.put("daily_records", dailyData);

        // 요약 통계
        Map<String, Object> statistics = buildStatistics(intakes, healthNotes, startDate, endDate);
        input.put("statistics", statistics);

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(input);
        } catch (JsonProcessingException e) {
            log.error("[PatternAnalysis] 프롬프트 JSON 생성 실패: ", e);
            throw new GeneralException(ErrorStatus.ANALYSIS_LLM_ERROR);
        }
    }

    /**
     * 일별 데이터 구성
     */
    private List<Map<String, Object>> buildDailyData(List<Intake> intakes, List<HealthNote> healthNotes,
                                                      LocalDate startDate, LocalDate endDate) {
        // 날짜별 복약 기록 그룹화
        Map<LocalDate, List<Intake>> intakesByDate = intakes.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        i -> i.getTakenAt().toLocalDate()
                ));

        // 날짜별 건강 메모 매핑
        Map<LocalDate, HealthNote> notesByDate = healthNotes.stream()
                .collect(java.util.stream.Collectors.toMap(
                        HealthNote::getNoteDate,
                        n -> n,
                        (a, b) -> a  // 중복 시 첫 번째 사용
                ));

        List<Map<String, Object>> dailyData = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            Map<String, Object> dayInfo = new LinkedHashMap<>();
            dayInfo.put("date", date.format(DateTimeFormatter.ISO_LOCAL_DATE));
            dayInfo.put("day_of_week", date.getDayOfWeek().name());

            // 해당 일의 복약 기록
            List<Intake> dayIntakes = intakesByDate.getOrDefault(date, List.of());
            int takenCount = (int) dayIntakes.stream()
                    .filter(i -> i.getStatus() == IntakeStatus.TAKEN)
                    .count();
            int skippedCount = (int) dayIntakes.stream()
                    .filter(i -> i.getStatus() == IntakeStatus.SKIPPED)
                    .count();
            int totalScheduled = dayIntakes.size();

            dayInfo.put("taken_count", takenCount);
            dayInfo.put("skipped_count", skippedCount);
            dayInfo.put("total_scheduled", totalScheduled);
            dayInfo.put("adherence_rate", totalScheduled > 0 ?
                    Math.round((double) takenCount / totalScheduled * 100.0) : null);

            // 시간대별 복약 (아침/점심/저녁/취침전)
            Map<String, Integer> timingCount = new LinkedHashMap<>();
            timingCount.put("MORNING", 0);
            timingCount.put("LUNCH", 0);
            timingCount.put("DINNER", 0);
            timingCount.put("BEDTIME", 0);

            for (Intake intake : dayIntakes) {
                if (intake.getStatus() == IntakeStatus.TAKEN && intake.getTiming() != null) {
                    String timing = intake.getTiming().name();
                    timingCount.merge(timing, 1, Integer::sum);
                }
            }
            dayInfo.put("timing_breakdown", timingCount);

            // 해당 일의 건강 메모
            HealthNote note = notesByDate.get(date);
            if (note != null) {
                dayInfo.put("condition_score", note.getConditionScore());
                dayInfo.put("note_content", truncateText(note.getContent(), 100));
            } else {
                dayInfo.put("condition_score", null);
                dayInfo.put("note_content", null);
            }

            dailyData.add(dayInfo);
        }

        return dailyData;
    }

    /**
     * 통계 정보 생성
     */
    private Map<String, Object> buildStatistics(List<Intake> intakes, List<HealthNote> healthNotes,
                                                 LocalDate startDate, LocalDate endDate) {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 전체 복약 통계
        long totalTaken = intakes.stream()
                .filter(i -> i.getStatus() == IntakeStatus.TAKEN)
                .count();
        long totalSkipped = intakes.stream()
                .filter(i -> i.getStatus() == IntakeStatus.SKIPPED)
                .count();
        long totalScheduled = intakes.size();

        stats.put("total_taken", totalTaken);
        stats.put("total_skipped", totalSkipped);
        stats.put("total_scheduled", totalScheduled);
        stats.put("overall_adherence_rate", totalScheduled > 0 ?
                Math.round((double) totalTaken / totalScheduled * 100.0) : 0);

        // 건강 메모 통계
        if (!healthNotes.isEmpty()) {
            double avgCondition = healthNotes.stream()
                    .mapToInt(HealthNote::getConditionScore)
                    .average()
                    .orElse(0);
            int minCondition = healthNotes.stream()
                    .mapToInt(HealthNote::getConditionScore)
                    .min()
                    .orElse(0);
            int maxCondition = healthNotes.stream()
                    .mapToInt(HealthNote::getConditionScore)
                    .max()
                    .orElse(10);

            stats.put("avg_condition_score", Math.round(avgCondition * 10.0) / 10.0);
            stats.put("min_condition_score", minCondition);
            stats.put("max_condition_score", maxCondition);
            stats.put("notes_count", healthNotes.size());
        } else {
            stats.put("avg_condition_score", null);
            stats.put("min_condition_score", null);
            stats.put("max_condition_score", null);
            stats.put("notes_count", 0);
        }

        return stats;
    }

    /**
     * 분석 기간 내 일별 컨디션 데이터 생성 (그래프용)
     */
    private List<AnalysisResponseDTO.DailyCondition> buildDailyConditions(
            User user, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return List.of();
        }

        // 분석 기간 내 건강 메모 조회
        List<HealthNote> healthNotes = healthNoteRepository.findByUserAndNoteDateBetween(
                user, startDate, endDate);

        if (healthNotes.isEmpty()) {
            return List.of();
        }

        // 해당 기간의 복약 기록 조회 (복약률 계산용)
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
        List<Intake> intakes = intakeRepository.findAllByUserIdAndDateRangeWithDetails(
                user.getId(), startDateTime, endDateTime);

        // 날짜별 복약 기록 그룹화
        Map<LocalDate, List<Intake>> intakesByDate = intakes.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        i -> i.getTakenAt().toLocalDate()
                ));

        // 건강 메모를 DailyCondition DTO로 변환
        return healthNotes.stream()
                .map(note -> {
                    LocalDate date = note.getNoteDate();

                    // 해당 일의 복약률 계산
                    List<Intake> dayIntakes = intakesByDate.getOrDefault(date, List.of());
                    Double adherenceRate = null;
                    if (!dayIntakes.isEmpty()) {
                        long takenCount = dayIntakes.stream()
                                .filter(i -> i.getStatus() == IntakeStatus.TAKEN)
                                .count();
                        adherenceRate = Math.round((double) takenCount / dayIntakes.size() * 100.0 * 10.0) / 10.0;
                    }

                    return AnalysisResponseDTO.DailyCondition.builder()
                            .date(date)
                            .conditionScore(note.getConditionScore())
                            .adherenceRate(adherenceRate)
                            .hasNote(note.getContent() != null && !note.getContent().isBlank())
                            .build();
                })
                .toList();
    }
}

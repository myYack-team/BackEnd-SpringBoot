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
            당신은 복약 습관 분석 시스템입니다.

            역할:
            - 사용자의 30일간 복약 기록과 건강 메모를 분석합니다
            - 복약 패턴과 컨디션 점수의 시간적 상관관계를 분석합니다
            - 건강 메모에서 증상 클러스터링 패턴을 감지합니다

            시간적 상관관계 분석 원칙:
            1. 복용 시간 변화와 컨디션 변화의 관계를 분석합니다
               - 특정 약물의 복용 시간이 변경된 후 컨디션 변화가 있는지 확인
            2. 복용 누락과 컨디션 하락의 관계를 분석합니다
               - 복용을 누락한 다음 날 또는 2-3일 후 컨디션 변화 관찰
            3. 꾸준한 복용과 컨디션 유지/개선의 관계를 분석합니다
            4. 각 약물별로 개별 분석합니다

            ## 증상 클러스터링 분석 (매우 중요)

            건강 메모에서 함께 나타나는 증상 조합을 분석하여 의미 있는 패턴을 감지합니다.
            사용자가 자유롭게 작성한 메모에서 증상을 추출하고 맥락을 파악해야 합니다.

            ### 심각도 기반 임계값 규칙

            #### HIGH 심각도 (1회만 나타나도 즉시 이벤트 생성)
            | 증상 조합 | 의심 상태 | 관련 약물 참고 |
            |----------|----------|---------------|
            | 시각 이상(번쩍임, 아지랑이, 시야 흐림) + 두통/편두통 | 섬광암점 (편두통 전조) | 경구 피임약 복용 시 뇌혈관 관련 |
            | 가슴 통증/답답함 + 호흡 곤란 | 심혈관계 증상 | 심장 관련 약물 |
            | 심한 어지러움 + 실신/의식 저하 | 저혈압/저혈당 | 혈압약, 당뇨약 |
            | 피부 발진/두드러기 + 호흡 곤란 | 알레르기 반응 | 새로 시작한 약물 |
            | 근육통 + 소변 색 변화(갈색) | 횡문근융해증 의심 | 스타틴 계열 |

            #### MEDIUM 심각도 (2회 이상 반복 시 이벤트 생성)
            | 증상 조합 | 설명 |
            |----------|------|
            | 소화불량 + 특정 약물 복용 시점 | 약물 부작용 가능성 |
            | 수면 장애 + 복용 시간 변경 | 복용 타이밍 영향 |
            | 피로감 + 특정 약물 누락 | 약물 효과 감소 |

            #### LOW 심각도 (3회 이상 반복 시 이벤트 생성)
            - 단순 컨디션 저하 (특정 증상 없음)
            - 일반적인 피로, 가벼운 두통 등

            ### 증상 추출 시 맥락 파악 예시
            - "오늘 눈앞이 번쩍거리고 머리가 아팠다" → 시각 이상 + 두통 (HIGH)
            - "아지랑이 같은게 보이더니 편두통이 왔다" → 시각 이상 + 편두통 (HIGH)
            - "약 먹고 나서 속이 안 좋았다" → 소화불량 + 약물 복용 (MEDIUM)
            - "요즘 자꾸 피곤하다" → 피로감 (LOW)

            문체 규칙:
            1. 간결하고 객관적인 어조를 사용합니다
            2. "~입니다", "~합니다", "~하세요", "~하는 경향이 있습니다" 형태로 작성합니다
            3. 절대 금지 표현: "인상적", "멋집니다", "기대해볼 수 있겠네요", "응원합니다" 등 개인적 감상이나 두루뭉술한 표현
            4. 절대 금지 표현: "위험", "경고", "심각", "주의", "문제" (대신 "확인이 필요합니다", "참고하세요" 사용)
            5. 의학적 판단이나 복용 지시를 하지 않습니다
            6. 구체적인 수치와 사실을 기반으로 작성합니다

            이벤트(events) 작성 규칙 - 매우 중요:
            1. 반드시 특정 약물과 컨디션/증상 메모 변화 사이의 상관관계만 이벤트로 추출합니다
            2. 이벤트로 추출해야 하는 것:
               - 특정 약물 복용 후 증상 메모에 기록된 변화 (예: "A약 복용 후 두통 완화 기록됨")
               - 특정 약물 누락 후 컨디션 점수 하락과 증상 메모 변화
               - 복용 시간 변경 후 증상 메모에 나타난 변화
               - 특정 약물 조합 복용일과 컨디션 점수/증상 메모의 상관관계
               - 증상 클러스터링 패턴 (심각도별 임계값 적용)
            3. 이벤트로 추출하지 말아야 하는 것 (절대 금지):
               - 단순 약 개수 변화 (예: "복용 약물이 N정으로 늘어남/줄어듦")
               - 당연한 패턴 (예: "N일 연속 복용 후 컨디션 좋아짐", "꾸준히 복용해서 안정적")
               - 단순 복약률 변화 (예: "이번 주 복약률 상승")
               - 구체적인 약물명이나 증상 메모 내용 없이 일반적인 진술
            4. 이벤트는 최대 7개까지만 추출합니다 (증상 클러스터 포함)
            5. 상관관계가 뚜렷하지 않으면 events 배열을 비워두세요
            6. 증상 클러스터 이벤트는 발생 횟수를 포함합니다 (예: "2번째 발생")

            출력 형식:
            반드시 아래 JSON 스키마에 맞춰 응답합니다. JSON만 출력하고 다른 텍스트는 포함하지 않습니다.

            {
              "adherenceAnalysis": {
                "overallRate": 0~100 사이 숫자 (전체 복약률),
                "weekdayPattern": {
                  "mondayRate": 숫자, "tuesdayRate": 숫자, "wednesdayRate": 숫자,
                  "thursdayRate": 숫자, "fridayRate": 숫자, "saturdayRate": 숫자, "sundayRate": 숫자,
                  "bestDay": "가장 복약률 높은 요일 (예: 월)",
                  "worstDay": "가장 복약률 낮은 요일 (예: 토)"
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
                  "description": "패턴 설명 1~2줄 (간결한 사실 기반)"
                }
              ],
              "summary": {
                "overallAssessment": "전반적인 평가 1~2문장 (객관적 사실 기반)",
                "positivePoint": "긍정적인 점 1문장 (사실 기반)",
                "improvementPoint": "개선이 필요한 점 1문장 (사실 기반)"
              },
              "symptomClusters": [
                {
                  "clusterName": "증상 조합 이름 (예: 시각 이상 + 두통)",
                  "severity": "HIGH/MEDIUM/LOW",
                  "occurrenceCount": 숫자,
                  "occurrenceDates": ["YYYY-MM-DD", ...],
                  "relatedMedications": ["관련 약물명", ...],
                  "description": "클러스터 설명 (맥락 포함)",
                  "suggestion": "참고 사항 (의학적 판단 없이)"
                }
              ],
              "events": [
                {
                  "date": "YYYY-MM-DD",
                  "eventType": "MEDICATION_SYMPTOM_CORRELATION/TIMING_CHANGE_EFFECT/MISSED_DOSE_EFFECT/COMBINATION_EFFECT/SYMPTOM_CLUSTER",
                  "eventIcon": "이모지 1개",
                  "title": "이벤트 제목 (구체적 약물명 또는 증상 포함, 20자 이내)",
                  "description": "이벤트 설명 (구체적 약물명과 증상/컨디션 변화 포함, 증상 클러스터의 경우 'N번째 발생' 포함)"
                }
              ]
            }
            """;

    @Override
    public AnalysisResponseDTO.AnalysisResult requestAnalysis(Long userId) {
        User user = findUserById(userId);

        // 1. 주간 쿼터 확인 및 리셋 처리
        UserAnalysisQuota quota = getOrCreateQuota(user);
        checkAndResetWeeklyQuotaIfNeeded(quota);

        if (!quota.canAnalyzeThisWeek()) {
            throw new GeneralException(ErrorStatus.ANALYSIS_WEEKLY_QUOTA_EXCEEDED);
        }

        // 2. 사용자 복용 약물 조회
        List<UserMedication> medications = userMedicationRepository.findByUserWithDrugInfo(user);
        if (medications.isEmpty()) {
            throw new GeneralException(ErrorStatus.ANALYSIS_NO_MEDICATIONS);
        }

        // 3. 약물 코드 및 성분명 추출
        List<String> itemSeqs = medications.stream()
                .filter(m -> m.getDrugInfo() != null)
                .map(m -> m.getDrugInfo().getItemSeq())
                .toList();

        List<String> ingredientNames = medications.stream()
                .filter(m -> m.getDrugInfo() != null && m.getDrugInfo().getIngredientKr() != null)
                .map(m -> m.getDrugInfo().getIngredientKr())
                .distinct()
                .toList();

        // 4. 약-약 상호작용 조회
        List<DrugInteraction> drugInteractions = itemSeqs.isEmpty() ?
                List.of() : drugInteractionRepository.findByDrugItemSeqsIn(itemSeqs);

        // 5. 약-음식 상호작용 조회
        List<DrugFoodInteraction> foodInteractions = (itemSeqs.isEmpty() && ingredientNames.isEmpty()) ?
                List.of() : drugFoodInteractionRepository.findByDrugItemSeqsOrIngredientNames(itemSeqs, ingredientNames);

        // 6. 사용자 복용 영양제 조회
        List<UserSupplement> userSupplements = userSupplementRepository.findByUserWithSupplement(user);

        // 7. LLM 입력 JSON 생성
        String userPrompt = buildUserPrompt(medications, drugInteractions, foodInteractions, userSupplements);
        log.info("[Analysis] LLM 입력 프롬프트 길이: {}", userPrompt.length());

        // 8. LLM 호출
        String llmResponse;
        try {
            llmResponse = llmClient.generate(SYSTEM_PROMPT, userPrompt);
            log.info("[Analysis] LLM 응답 길이: {}", llmResponse.length());
        } catch (Exception e) {
            log.error("[Analysis] LLM 호출 실패: ", e);
            throw new GeneralException(ErrorStatus.ANALYSIS_LLM_ERROR);
        }

        // 9. JSON 추출 및 검증
        String cleanedResponse = extractJsonFromResponse(llmResponse);

        // 10. 패턴 분석 수행
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(PATTERN_ANALYSIS_DAYS - 1);
        String patternAnalysisJson = performPatternAnalysis(userId, startDate, endDate);

        // 11. 레포트 저장
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

        // 12. 주간 쿼터 사용량 증가
        quota.incrementWeeklyUsedCount();

        log.info("[Analysis] 분석 완료 - userId: {}, reportId: {}, mechanisms: {}, foods: {}",
                userId, report.getId(), mechanismGroupCount, foodInteractionCount);

        return AnalysisConverter.toAnalysisResult(report, quota);
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
                            .weeklyLimit(3)
                            .weeklyUsedCount(0)
                            .weeklyResetDate(getNextMonday())
                            .build();
                    return userAnalysisQuotaRepository.save(newQuota);
                });
    }

    private void checkAndResetWeeklyQuotaIfNeeded(UserAnalysisQuota quota) {
        LocalDate today = LocalDate.now();
        if (quota.needsWeeklyReset(today)) {
            quota.resetWeeklyQuota(getNextMonday());
            log.info("[Analysis] 주간 쿼터 리셋 - userId: {}", quota.getUser().getId());
        }
    }

    private LocalDate getNextMonday() {
        LocalDate today = LocalDate.now();
        int daysUntilMonday = (8 - today.getDayOfWeek().getValue()) % 7;
        if (daysUntilMonday == 0) {
            daysUntilMonday = 7;
        }
        return today.plusDays(daysUntilMonday);
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

        // 복용 중인 약물 목록 (중복 제거)
        Set<Long> medicationIds = new HashSet<>();
        List<Map<String, Object>> medications = new ArrayList<>();
        for (Intake intake : intakes) {
            Long medId = getMedicationIdFromIntake(intake);
            if (medId != null && !medicationIds.contains(medId)) {
                medicationIds.add(medId);
                Map<String, Object> medInfo = new LinkedHashMap<>();
                medInfo.put("id", medId);
                medInfo.put("name", getMedicationNameFromIntake(intake));
                medInfo.put("ingredient", getMedicationIngredientFromIntake(intake));
                medications.add(medInfo);
            }
        }
        input.put("medications", medications);

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
     * Intake에서 약물/영양제 ID 추출
     */
    private Long getMedicationIdFromIntake(Intake intake) {
        if (intake.getUserMedication() != null) {
            return intake.getUserMedication().getId();
        }
        if (intake.getUserSupplement() != null) {
            return intake.getUserSupplement().getId();
        }
        return null;
    }

    /**
     * Intake에서 약물/영양제명 추출
     */
    private String getMedicationNameFromIntake(Intake intake) {
        if (intake.getUserMedication() != null) {
            UserMedication um = intake.getUserMedication();
            if (um.getDrugInfo() != null) {
                return um.getDrugInfo().getDisplayName() != null
                        ? um.getDrugInfo().getDisplayName()
                        : um.getDrugInfo().getItemName();
            }
            return um.getCustomDrugName();
        }
        if (intake.getUserSupplement() != null) {
            return intake.getUserSupplement().getSupplement().getName();
        }
        return null;
    }

    /**
     * Intake에서 성분명 추출 (의약품만 해당)
     */
    private String getMedicationIngredientFromIntake(Intake intake) {
        if (intake.getUserMedication() != null && intake.getUserMedication().getDrugInfo() != null) {
            return intake.getUserMedication().getDrugInfo().getIngredientKr();
        }
        return null;
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

            // 개별 복약 기록 (taken_at 포함)
            List<Map<String, Object>> intakeDetails = new ArrayList<>();
            for (Intake intake : dayIntakes) {
                Map<String, Object> intakeInfo = new LinkedHashMap<>();
                intakeInfo.put("medication_id", getMedicationIdFromIntake(intake));
                intakeInfo.put("medication_name", getMedicationNameFromIntake(intake));
                intakeInfo.put("timing", intake.getTiming() != null ? intake.getTiming().name() : null);
                intakeInfo.put("status", intake.getStatus().name());
                if (intake.getStatus() == IntakeStatus.TAKEN && intake.getTakenAt() != null) {
                    intakeInfo.put("taken_at", intake.getTakenAt().toLocalTime()
                            .format(DateTimeFormatter.ofPattern("HH:mm")));
                } else {
                    intakeInfo.put("taken_at", null);
                }
                intakeDetails.add(intakeInfo);
            }
            dayInfo.put("intakes", intakeDetails);

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
                            .content(note.getContent())
                            .build();
                })
                .toList();
    }
}

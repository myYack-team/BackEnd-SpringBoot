package com.myyak.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myyak.domain.AnalysisReport;
import com.myyak.domain.UserAnalysisQuota;
import com.myyak.web.dto.AnalysisDTO.AnalysisResponseDTO;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * AI 약물 분석 Converter
 */
@Slf4j
public class AnalysisConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 레포트 엔티티 → 분석 결과 DTO
     */
    public static AnalysisResponseDTO.AnalysisResult toAnalysisResult(
            AnalysisReport report,
            UserAnalysisQuota quota) {
        return toAnalysisResult(report, quota, List.of());
    }

    /**
     * 레포트 엔티티 → 분석 결과 DTO (dailyConditions 포함)
     */
    public static AnalysisResponseDTO.AnalysisResult toAnalysisResult(
            AnalysisReport report,
            UserAnalysisQuota quota,
            List<AnalysisResponseDTO.DailyCondition> dailyConditions) {

        String llmResponse = report.getLlmResponse();

        // LLM 응답 JSON 파싱
        List<AnalysisResponseDTO.MechanismGroup> mechanismGroups = parseMechanismGroups(llmResponse);
        List<AnalysisResponseDTO.FoodInteractionSummary> foodInteractions = parseFoodInteractions(llmResponse);
        List<AnalysisResponseDTO.FoodSuggestion> foodSuggestions = parseFoodSuggestions(llmResponse);
        List<AnalysisResponseDTO.LifestyleTip> lifestyleTips = parseLifestyleTips(llmResponse);

        // 패턴 분석 파싱 (dailyConditions 전달)
        AnalysisResponseDTO.PatternAnalysis patternAnalysis = parsePatternAnalysis(
                report.getPatternAnalysis(),
                report.getAnalysisStartDate(),
                report.getAnalysisEndDate(),
                dailyConditions
        );

        return AnalysisResponseDTO.AnalysisResult.builder()
                .reportId(report.getId())
                .analysisDate(report.getAnalysisDate())
                .mechanismGroups(mechanismGroups)
                .foodInteractions(foodInteractions)
                .foodSuggestions(foodSuggestions)
                .lifestyleTips(lifestyleTips)
                .patternAnalysis(patternAnalysis)
                .quota(toQuotaInfo(quota))
                .build();
    }

    /**
     * 레포트 엔티티 → 레포트 요약 DTO
     */
    public static AnalysisResponseDTO.ReportSummary toReportSummary(AnalysisReport report) {
        return AnalysisResponseDTO.ReportSummary.builder()
                .id(report.getId())
                .analysisDate(report.getAnalysisDate())
                .mechanismGroupCount(report.getMechanismGroupCount())
                .foodInteractionCount(report.getFoodInteractionCount())
                .build();
    }

    /**
     * 레포트 목록 → 레포트 목록 DTO
     */
    public static AnalysisResponseDTO.ReportList toReportList(List<AnalysisReport> reports) {
        List<AnalysisResponseDTO.ReportSummary> summaries = reports.stream()
                .map(AnalysisConverter::toReportSummary)
                .toList();

        return AnalysisResponseDTO.ReportList.builder()
                .reports(summaries)
                .totalCount(summaries.size())
                .build();
    }

    /**
     * 쿼터 엔티티 → 쿼터 정보 DTO (월간)
     */
    public static AnalysisResponseDTO.QuotaInfo toQuotaInfo(UserAnalysisQuota quota) {
        if (quota == null) {
            return AnalysisResponseDTO.QuotaInfo.builder()
                    .monthlyLimit(2)
                    .monthlyUsedCount(0)
                    .monthlyRemainingCount(2)
                    .monthlyResetDate(null)
                    .build();
        }

        return AnalysisResponseDTO.QuotaInfo.builder()
                .monthlyLimit(quota.getMonthlyLimit())
                .monthlyUsedCount(quota.getMonthlyUsedCount())
                .monthlyRemainingCount(quota.getMonthlyRemainingCount())
                .monthlyResetDate(quota.getMonthlyResetDate())
                .build();
    }

    /**
     * LLM 응답 JSON에서 기전 그룹 파싱
     */
    @SuppressWarnings("unchecked")
    private static List<AnalysisResponseDTO.MechanismGroup> parseMechanismGroups(String llmResponse) {
        try {
            Map<String, Object> response = objectMapper.readValue(llmResponse, new TypeReference<>() {});
            List<Map<String, Object>> groups = (List<Map<String, Object>>) response.get("mechanismGroups");

            if (groups == null) {
                return List.of();
            }

            return groups.stream()
                    .map(g -> {
                        // medications 리스트 파싱
                        List<Map<String, String>> medicationMaps = (List<Map<String, String>>) g.get("medications");
                        List<AnalysisResponseDTO.MechanismMedication> medications = medicationMaps != null ?
                                medicationMaps.stream()
                                        .map(m -> AnalysisResponseDTO.MechanismMedication.builder()
                                                .name(m.get("name"))
                                                .ingredientName(m.get("ingredientName"))
                                                .build())
                                        .toList()
                                : List.of();

                        return AnalysisResponseDTO.MechanismGroup.builder()
                                .categoryName((String) g.get("categoryName"))
                                .categoryIcon((String) g.get("categoryIcon"))
                                .description((String) g.get("description"))
                                .analogy((String) g.get("analogy"))
                                .medicationCount(g.get("medicationCount") != null ?
                                        ((Number) g.get("medicationCount")).intValue() : 0)
                                .medications(medications)
                                .build();
                    })
                    .toList();

        } catch (JsonProcessingException e) {
            log.error("기전 그룹 파싱 실패: ", e);
            return List.of();
        }
    }

    /**
     * LLM 응답 JSON에서 음식 상호작용 파싱
     */
    @SuppressWarnings("unchecked")
    private static List<AnalysisResponseDTO.FoodInteractionSummary> parseFoodInteractions(String llmResponse) {
        try {
            Map<String, Object> response = objectMapper.readValue(llmResponse, new TypeReference<>() {});
            List<Map<String, Object>> foods = (List<Map<String, Object>>) response.get("foodInteractions");

            if (foods == null) {
                return List.of();
            }

            return foods.stream()
                    .map(f -> {
                        List<Map<String, String>> detailMaps = (List<Map<String, String>>) f.get("details");
                        List<AnalysisResponseDTO.FoodInteractionDetail> details = detailMaps != null ?
                                detailMaps.stream()
                                        .map(d -> AnalysisResponseDTO.FoodInteractionDetail.builder()
                                                .medicationName(d.get("medicationName"))
                                                .reason(d.get("reason"))
                                                .build())
                                        .toList()
                                : List.of();

                        return AnalysisResponseDTO.FoodInteractionSummary.builder()
                                .foodName((String) f.get("foodName"))
                                .foodIcon((String) f.get("foodIcon"))
                                .interactionLevel((String) f.get("interactionLevel"))
                                .affectedMedicationCount(f.get("affectedMedicationCount") != null ?
                                        ((Number) f.get("affectedMedicationCount")).intValue() : 0)
                                .summaryReason((String) f.get("summaryReason"))
                                .details(details)
                                .build();
                    })
                    .toList();

        } catch (JsonProcessingException e) {
            log.error("음식 상호작용 파싱 실패: ", e);
            return List.of();
        }
    }

    /**
     * LLM 응답 JSON에서 음식 제안 파싱
     */
    @SuppressWarnings("unchecked")
    private static List<AnalysisResponseDTO.FoodSuggestion> parseFoodSuggestions(String llmResponse) {
        try {
            Map<String, Object> response = objectMapper.readValue(llmResponse, new TypeReference<>() {});
            List<Map<String, Object>> suggestions = (List<Map<String, Object>>) response.get("foodSuggestions");

            if (suggestions == null) {
                return List.of();
            }

            return suggestions.stream()
                    .map(s -> {
                        List<Map<String, String>> relatedMedMaps = (List<Map<String, String>>) s.get("relatedMedications");
                        List<AnalysisResponseDTO.RelatedMedication> relatedMedications = parseRelatedMedications(relatedMedMaps);

                        return AnalysisResponseDTO.FoodSuggestion.builder()
                                .foodName((String) s.get("foodName"))
                                .foodIcon((String) s.get("foodIcon"))
                                .reason((String) s.get("reason"))
                                .tip((String) s.get("tip"))
                                .relatedMedications(relatedMedications)
                                .build();
                    })
                    .toList();

        } catch (JsonProcessingException e) {
            log.error("음식 제안 파싱 실패: ", e);
            return List.of();
        }
    }

    /**
     * LLM 응답 JSON에서 영양제 상호작용 파싱
     */
    @SuppressWarnings("unchecked")
    private static List<AnalysisResponseDTO.SupplementInteraction> parseSupplementInteractions(String llmResponse) {
        try {
            Map<String, Object> response = objectMapper.readValue(llmResponse, new TypeReference<>() {});
            List<Map<String, Object>> interactions = (List<Map<String, Object>>) response.get("supplementInteractions");

            if (interactions == null) {
                return List.of();
            }

            return interactions.stream()
                    .map(i -> {
                        List<Map<String, String>> detailMaps = (List<Map<String, String>>) i.get("details");
                        List<AnalysisResponseDTO.SupplementDetail> details = detailMaps != null ?
                                detailMaps.stream()
                                        .map(d -> AnalysisResponseDTO.SupplementDetail.builder()
                                                .medicationName(d.get("medicationName"))
                                                .reason(d.get("reason"))
                                                .build())
                                        .toList()
                                : List.of();

                        return AnalysisResponseDTO.SupplementInteraction.builder()
                                .supplementName((String) i.get("supplementName"))
                                .supplementTag((String) i.get("supplementTag"))
                                .interactionLevel((String) i.get("interactionLevel"))
                                .summaryReason((String) i.get("summaryReason"))
                                .source((String) i.get("source"))
                                .details(details)
                                .build();
                    })
                    .toList();

        } catch (JsonProcessingException e) {
            log.error("영양제 상호작용 파싱 실패: ", e);
            return List.of();
        }
    }

    /**
     * LLM 응답 JSON에서 생활 팁 파싱
     */
    @SuppressWarnings("unchecked")
    private static List<AnalysisResponseDTO.LifestyleTip> parseLifestyleTips(String llmResponse) {
        try {
            Map<String, Object> response = objectMapper.readValue(llmResponse, new TypeReference<>() {});
            List<Map<String, Object>> tips = (List<Map<String, Object>>) response.get("lifestyleTips");

            if (tips == null) {
                return List.of();
            }

            return tips.stream()
                    .map(t -> {
                        List<Map<String, String>> relatedMedMaps = (List<Map<String, String>>) t.get("relatedMedications");
                        List<AnalysisResponseDTO.RelatedMedication> relatedMedications = parseRelatedMedications(relatedMedMaps);

                        return AnalysisResponseDTO.LifestyleTip.builder()
                                .category((String) t.get("category"))
                                .categoryIcon((String) t.get("categoryIcon"))
                                .categoryLabel((String) t.get("categoryLabel"))
                                .title((String) t.get("title"))
                                .tip((String) t.get("tip"))
                                .detailedExplanation((String) t.get("detailedExplanation"))
                                .source((String) t.get("source"))
                                .relatedMedications(relatedMedications)
                                .build();
                    })
                    .toList();

        } catch (JsonProcessingException e) {
            log.error("생활 팁 파싱 실패: ", e);
            return List.of();
        }
    }

    /**
     * 관련 약물 목록 파싱 (공통 메서드)
     */
    private static List<AnalysisResponseDTO.RelatedMedication> parseRelatedMedications(List<Map<String, String>> maps) {
        if (maps == null) {
            return List.of();
        }

        return maps.stream()
                .map(m -> AnalysisResponseDTO.RelatedMedication.builder()
                        .name(m.get("name"))
                        .detail(m.get("detail"))
                        .build())
                .toList();
    }

    // ===== 패턴 분석 파싱 메서드 =====

    /**
     * 패턴 분석 JSON 파싱
     */
    @SuppressWarnings("unchecked")
    private static AnalysisResponseDTO.PatternAnalysis parsePatternAnalysis(
            String patternJson, LocalDate startDate, LocalDate endDate,
            List<AnalysisResponseDTO.DailyCondition> dailyConditions) {
        if (patternJson == null || patternJson.isBlank()) {
            return null;
        }

        try {
            Map<String, Object> response = objectMapper.readValue(patternJson, new TypeReference<>() {});

            // 복약 순응도 분석
            AnalysisResponseDTO.AdherenceAnalysis adherenceAnalysis = parseAdherenceAnalysis(
                    (Map<String, Object>) response.get("adherenceAnalysis"));

            // 패턴 목록
            List<AnalysisResponseDTO.Pattern> patterns = parsePatterns(
                    (List<Map<String, Object>>) response.get("patterns"));

            // 인사이트 목록
            List<AnalysisResponseDTO.Insight> insights = parseInsights(
                    (List<Map<String, Object>>) response.get("insights"));

            // 요약
            AnalysisResponseDTO.PatternSummary summary = parseSummary(
                    (Map<String, Object>) response.get("summary"));

            // 타임라인 이벤트
            List<AnalysisResponseDTO.TimelineEvent> events = parseEvents(
                    (List<Map<String, Object>>) response.get("events"));

            return AnalysisResponseDTO.PatternAnalysis.builder()
                    .analysisStartDate(startDate)
                    .analysisEndDate(endDate)
                    .adherenceAnalysis(adherenceAnalysis)
                    .patterns(patterns)
                    .insights(insights)
                    .summary(summary)
                    .dailyConditions(dailyConditions != null ? dailyConditions : List.of())
                    .events(events)
                    .build();

        } catch (JsonProcessingException e) {
            log.error("패턴 분석 파싱 실패: ", e);
            return null;
        }
    }

    /**
     * 복약 순응도 분석 파싱
     */
    @SuppressWarnings("unchecked")
    private static AnalysisResponseDTO.AdherenceAnalysis parseAdherenceAnalysis(Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        // 요일별 패턴
        AnalysisResponseDTO.WeekdayPattern weekdayPattern = null;
        Map<String, Object> weekdayData = (Map<String, Object>) data.get("weekdayPattern");
        if (weekdayData != null) {
            weekdayPattern = AnalysisResponseDTO.WeekdayPattern.builder()
                    .mondayRate(toDouble(weekdayData.get("mondayRate")))
                    .tuesdayRate(toDouble(weekdayData.get("tuesdayRate")))
                    .wednesdayRate(toDouble(weekdayData.get("wednesdayRate")))
                    .thursdayRate(toDouble(weekdayData.get("thursdayRate")))
                    .fridayRate(toDouble(weekdayData.get("fridayRate")))
                    .saturdayRate(toDouble(weekdayData.get("saturdayRate")))
                    .sundayRate(toDouble(weekdayData.get("sundayRate")))
                    .bestDay((String) weekdayData.get("bestDay"))
                    .worstDay((String) weekdayData.get("worstDay"))
                    .build();
        }

        // 시간대별 패턴
        AnalysisResponseDTO.TimingPattern timingPattern = null;
        Map<String, Object> timingData = (Map<String, Object>) data.get("timingPattern");
        if (timingData != null) {
            timingPattern = AnalysisResponseDTO.TimingPattern.builder()
                    .morningRate(toDouble(timingData.get("morningRate")))
                    .lunchRate(toDouble(timingData.get("lunchRate")))
                    .dinnerRate(toDouble(timingData.get("dinnerRate")))
                    .bedtimeRate(toDouble(timingData.get("bedtimeRate")))
                    .bestTiming((String) timingData.get("bestTiming"))
                    .worstTiming((String) timingData.get("worstTiming"))
                    .build();
        }

        return AnalysisResponseDTO.AdherenceAnalysis.builder()
                .overallRate(toDouble(data.get("overallRate")))
                .weekdayPattern(weekdayPattern)
                .timingPattern(timingPattern)
                .missedDays(toInteger(data.get("missedDays")))
                .perfectDays(toInteger(data.get("perfectDays")))
                .build();
    }

    /**
     * 패턴 목록 파싱
     */
    private static List<AnalysisResponseDTO.Pattern> parsePatterns(List<Map<String, Object>> patternList) {
        if (patternList == null) {
            return List.of();
        }

        return patternList.stream()
                .map(p -> AnalysisResponseDTO.Pattern.builder()
                        .patternType((String) p.get("patternType"))
                        .patternIcon((String) p.get("patternIcon"))
                        .title((String) p.get("title"))
                        .description((String) p.get("description"))
                        .suggestion((String) p.get("suggestion"))
                        .build())
                .toList();
    }

    /**
     * 인사이트 목록 파싱
     */
    private static List<AnalysisResponseDTO.Insight> parseInsights(List<Map<String, Object>> insightList) {
        if (insightList == null) {
            return List.of();
        }

        return insightList.stream()
                .map(i -> AnalysisResponseDTO.Insight.builder()
                        .insightType((String) i.get("insightType"))
                        .insightIcon((String) i.get("insightIcon"))
                        .title((String) i.get("title"))
                        .description((String) i.get("description"))
                        .actionItem((String) i.get("actionItem"))
                        .build())
                .toList();
    }

    /**
     * 요약 파싱
     */
    private static AnalysisResponseDTO.PatternSummary parseSummary(Map<String, Object> summaryData) {
        if (summaryData == null) {
            return null;
        }

        return AnalysisResponseDTO.PatternSummary.builder()
                .overallAssessment((String) summaryData.get("overallAssessment"))
                .positivePoint((String) summaryData.get("positivePoint"))
                .improvementPoint((String) summaryData.get("improvementPoint"))
                .encouragement((String) summaryData.get("encouragement"))
                .build();
    }

    /**
     * 타임라인 이벤트 파싱
     */
    private static List<AnalysisResponseDTO.TimelineEvent> parseEvents(List<Map<String, Object>> eventList) {
        if (eventList == null) {
            return List.of();
        }

        return eventList.stream()
                .map(e -> {
                    LocalDate date = null;
                    String dateStr = (String) e.get("date");
                    if (dateStr != null) {
                        try {
                            date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
                        } catch (Exception ex) {
                            log.warn("이벤트 날짜 파싱 실패: {}", dateStr);
                        }
                    }

                    return AnalysisResponseDTO.TimelineEvent.builder()
                            .date(date)
                            .eventType((String) e.get("eventType"))
                            .eventIcon((String) e.get("eventIcon"))
                            .title((String) e.get("title"))
                            .description((String) e.get("description"))
                            .build();
                })
                .toList();
    }

    // ===== 유틸리티 메서드 =====

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }
}

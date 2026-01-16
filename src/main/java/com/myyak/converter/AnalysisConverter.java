package com.myyak.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myyak.domain.AnalysisReport;
import com.myyak.domain.UserAnalysisQuota;
import com.myyak.web.dto.AnalysisDTO.AnalysisResponseDTO;
import lombok.extern.slf4j.Slf4j;

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

        String llmResponse = report.getLlmResponse();

        // LLM 응답 JSON 파싱
        List<AnalysisResponseDTO.MechanismGroup> mechanismGroups = parseMechanismGroups(llmResponse);
        List<AnalysisResponseDTO.FoodInteractionSummary> foodInteractions = parseFoodInteractions(llmResponse);
        List<AnalysisResponseDTO.FoodSuggestion> foodSuggestions = parseFoodSuggestions(llmResponse);
        List<AnalysisResponseDTO.SupplementInteraction> supplementInteractions = parseSupplementInteractions(llmResponse);
        List<AnalysisResponseDTO.LifestyleTip> lifestyleTips = parseLifestyleTips(llmResponse);

        return AnalysisResponseDTO.AnalysisResult.builder()
                .reportId(report.getId())
                .analysisDate(report.getAnalysisDate())
                .mechanismGroups(mechanismGroups)
                .foodInteractions(foodInteractions)
                .foodSuggestions(foodSuggestions)
                .supplementInteractions(supplementInteractions)
                .lifestyleTips(lifestyleTips)
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
     * 쿼터 엔티티 → 쿼터 정보 DTO
     */
    public static AnalysisResponseDTO.QuotaInfo toQuotaInfo(UserAnalysisQuota quota) {
        if (quota == null) {
            return AnalysisResponseDTO.QuotaInfo.builder()
                    .monthlyLimit(3)
                    .usedCount(0)
                    .remainingCount(3)
                    .resetDate(null)
                    .build();
        }

        return AnalysisResponseDTO.QuotaInfo.builder()
                .monthlyLimit(quota.getMonthlyLimit())
                .usedCount(quota.getUsedCount())
                .remainingCount(quota.getRemainingCount())
                .resetDate(quota.getResetDate() != null ?
                        quota.getResetDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : null)
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
}

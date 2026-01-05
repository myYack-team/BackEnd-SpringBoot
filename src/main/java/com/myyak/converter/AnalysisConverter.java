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

        // LLM 응답 JSON 파싱
        List<AnalysisResponseDTO.MechanismGroup> mechanismGroups = parseMechanismGroups(report.getLlmResponse());
        List<AnalysisResponseDTO.FoodInteractionSummary> foodInteractions = parseFoodInteractions(report.getLlmResponse());

        return AnalysisResponseDTO.AnalysisResult.builder()
                .reportId(report.getId())
                .analysisDate(report.getAnalysisDate())
                .mechanismGroups(mechanismGroups)
                .foodInteractions(foodInteractions)
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
}

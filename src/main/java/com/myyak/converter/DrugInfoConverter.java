package com.myyak.converter;

import com.myyak.domain.DrugInfo;
import com.myyak.web.dto.DrugInfoDTO.DrugInfoResponseDTO;

import java.util.List;
import java.util.stream.Collectors;

public class DrugInfoConverter {

    public static DrugInfoResponseDTO.DrugInfoDetail toDetail(DrugInfo drugInfo) {
        return DrugInfoResponseDTO.DrugInfoDetail.builder()
                .itemSeq(drugInfo.getItemSeq())
                .itemName(drugInfo.getItemName())
                .entpName(drugInfo.getEntpName())
                .efficacy(drugInfo.getEfficacy())
                .useMethod(drugInfo.getUseMethod())
                .warning(drugInfo.getWarning())
                .caution(drugInfo.getCaution())
                .interaction(drugInfo.getInteraction())
                .sideEffect(drugInfo.getSideEffect())
                .storageMethod(drugInfo.getStorageMethod())
                .imageUrl(drugInfo.getImageUrl())
                .build();
    }

    public static DrugInfoResponseDTO.DrugInfoSummary toSummary(DrugInfo drugInfo) {
        // 효능 요약 (첫 100자)
        String efficacySummary = drugInfo.getEfficacy();
        if (efficacySummary != null && efficacySummary.length() > 100) {
            efficacySummary = efficacySummary.substring(0, 100) + "...";
        }

        return DrugInfoResponseDTO.DrugInfoSummary.builder()
                .itemSeq(drugInfo.getItemSeq())
                .itemName(drugInfo.getItemName())
                .entpName(drugInfo.getEntpName())
                .efficacy(efficacySummary)
                .imageUrl(drugInfo.getImageUrl())
                .build();
    }

    public static DrugInfoResponseDTO.DrugSearchResult toSearchResult(List<DrugInfo> drugs) {
        List<DrugInfoResponseDTO.DrugInfoSummary> summaries = drugs.stream()
                .map(DrugInfoConverter::toSummary)
                .collect(Collectors.toList());

        return DrugInfoResponseDTO.DrugSearchResult.builder()
                .drugs(summaries)
                .totalCount(summaries.size())
                .build();
    }
}

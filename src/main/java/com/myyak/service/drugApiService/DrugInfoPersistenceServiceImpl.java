package com.myyak.service.drugApiService;

import com.myyak.domain.DrugInfo;
import com.myyak.domain.DrugInfoTest;
import com.myyak.repository.DrugInfoRepository;
import com.myyak.repository.DrugInfoTestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrugInfoPersistenceServiceImpl implements DrugInfoPersistenceService {

    private final DrugInfoRepository drugInfoRepository;
    private final DrugInfoTestRepository drugInfoTestRepository;

    @Override
    @Transactional
    public int saveEasyDrugPage(List<DrugInfo> drugInfos) {
        if (drugInfos == null || drugInfos.isEmpty()) {
            return 0;
        }

        // itemSeq 목록으로 기존 데이터 1회 조회 (건별 findById 제거)
        Map<String, DrugInfo> existingMap = findExistingMap(drugInfos);

        List<DrugInfo> newDrugs = new ArrayList<>();
        for (DrugInfo drugInfo : drugInfos) {
            DrugInfo existing = existingMap.get(drugInfo.getItemSeq());
            if (existing != null) {
                // 기존 데이터가 있으면 업데이트 (더티 체킹으로 반영)
                existing.updateFromApi(
                        drugInfo.getItemName(),
                        drugInfo.getDisplayName(),
                        drugInfo.getIngredientKr(),
                        drugInfo.getEntpName(),
                        drugInfo.getEfficacy(),
                        drugInfo.getUsage(),
                        drugInfo.getWarning(),
                        drugInfo.getCaution(),
                        drugInfo.getInteraction(),
                        drugInfo.getSideEffect(),
                        drugInfo.getStorageMethod(),
                        drugInfo.getImageUrl(),
                        drugInfo.getOpenDate(),
                        drugInfo.getApiUpdateDate()
                );
            } else {
                newDrugs.add(drugInfo);
            }
        }

        drugInfoRepository.saveAll(newDrugs);
        return drugInfos.size();
    }

    @Override
    @Transactional
    public int savePermitDrugPage(List<DrugInfo> drugInfos) {
        if (drugInfos == null || drugInfos.isEmpty()) {
            return 0;
        }

        // itemSeq 목록으로 기존 데이터 1회 조회 (건별 findById 제거)
        Map<String, DrugInfo> existingMap = findExistingMap(drugInfos);

        List<DrugInfo> newDrugs = new ArrayList<>();
        for (DrugInfo drugInfo : drugInfos) {
            DrugInfo existing = existingMap.get(drugInfo.getItemSeq());
            if (existing != null) {
                // 기존 데이터가 있으면 업데이트 (더티 체킹으로 반영)
                existing.updateFromPermitApi(
                        drugInfo.getItemName(),
                        drugInfo.getDisplayName(),
                        drugInfo.getIngredientKr(),
                        drugInfo.getEntpName(),
                        drugInfo.getDrugType(),
                        drugInfo.getIngredientName(),
                        drugInfo.getEfficacy(),
                        drugInfo.getUsage(),
                        drugInfo.getCaution(),
                        drugInfo.getStorageMethod(),
                        drugInfo.getImageUrl(),
                        drugInfo.getPermitDate()
                );
            } else {
                newDrugs.add(drugInfo);
            }
        }

        drugInfoRepository.saveAll(newDrugs);
        return drugInfos.size();
    }

    @Override
    @Transactional
    public int saveEasyDrugTestPage(List<DrugInfoTest> drugInfos) {
        if (drugInfos == null || drugInfos.isEmpty()) {
            return 0;
        }

        Map<String, DrugInfoTest> existingMap = findExistingTestMap(drugInfos);

        List<DrugInfoTest> newDrugs = new ArrayList<>();
        for (DrugInfoTest drugInfo : drugInfos) {
            DrugInfoTest existing = existingMap.get(drugInfo.getItemSeq());
            if (existing != null) {
                existing.updateFromApi(
                        drugInfo.getItemName(),
                        drugInfo.getDisplayName(),
                        drugInfo.getIngredientKr(),
                        drugInfo.getEntpName(),
                        drugInfo.getEfficacy(),
                        drugInfo.getUsage(),
                        drugInfo.getWarning(),
                        drugInfo.getCaution(),
                        drugInfo.getInteraction(),
                        drugInfo.getSideEffect(),
                        drugInfo.getStorageMethod(),
                        drugInfo.getImageUrl(),
                        drugInfo.getOpenDate(),
                        drugInfo.getApiUpdateDate()
                );
            } else {
                newDrugs.add(drugInfo);
            }
        }

        drugInfoTestRepository.saveAll(newDrugs);
        return drugInfos.size();
    }

    @Override
    @Transactional
    public int savePermitDrugTestPage(List<DrugInfoTest> drugInfos) {
        if (drugInfos == null || drugInfos.isEmpty()) {
            return 0;
        }

        Map<String, DrugInfoTest> existingMap = findExistingTestMap(drugInfos);

        List<DrugInfoTest> newDrugs = new ArrayList<>();
        for (DrugInfoTest drugInfo : drugInfos) {
            DrugInfoTest existing = existingMap.get(drugInfo.getItemSeq());
            if (existing != null) {
                existing.updateFromPermitApi(
                        drugInfo.getItemName(),
                        drugInfo.getDisplayName(),
                        drugInfo.getIngredientKr(),
                        drugInfo.getEntpName(),
                        drugInfo.getDrugType(),
                        drugInfo.getIngredientName(),
                        drugInfo.getEfficacy(),
                        drugInfo.getUsage(),
                        drugInfo.getCaution(),
                        drugInfo.getStorageMethod(),
                        drugInfo.getImageUrl(),
                        drugInfo.getPermitDate()
                );
            } else {
                newDrugs.add(drugInfo);
            }
        }

        drugInfoTestRepository.saveAll(newDrugs);
        return drugInfos.size();
    }

    // === Private Helper Methods ===

    private Map<String, DrugInfo> findExistingMap(List<DrugInfo> drugInfos) {
        List<String> itemSeqs = drugInfos.stream()
                .map(DrugInfo::getItemSeq)
                .toList();

        return drugInfoRepository.findAllById(itemSeqs).stream()
                .collect(Collectors.toMap(DrugInfo::getItemSeq, Function.identity()));
    }

    private Map<String, DrugInfoTest> findExistingTestMap(List<DrugInfoTest> drugInfos) {
        List<String> itemSeqs = drugInfos.stream()
                .map(DrugInfoTest::getItemSeq)
                .toList();

        return drugInfoTestRepository.findAllById(itemSeqs).stream()
                .collect(Collectors.toMap(DrugInfoTest::getItemSeq, Function.identity()));
    }
}

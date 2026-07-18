package com.myyak.service.drugBatchService;

import com.myyak.domain.DrugInfo;
import com.myyak.domain.DrugInfoTest;
import com.myyak.repository.DrugInfoRepository;
import com.myyak.repository.DrugInfoTestRepository;
import com.myyak.web.dto.DrugBatchDTO.DrugBatchResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 배치 검증 서비스 구현체
 * 테스트 테이블과 운영 테이블의 데이터를 비교하여 검증 결과를 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchVerificationServiceImpl implements BatchVerificationService {

    private final DrugInfoRepository drugInfoRepository;
    private final DrugInfoTestRepository drugInfoTestRepository;

    private static final int MAX_MISMATCH_DETAILS = 100;

    @Override
    @Transactional(readOnly = true)
    public DrugBatchResponseDTO.VerificationResponse verify(String jobId) {
        log.info("검증 시작: jobId={}", jobId);

        long originalCount = drugInfoRepository.count();
        long testCount = drugInfoTestRepository.countAll();

        log.info("원본 테이블 건수: {}, 테스트 테이블 건수: {}", originalCount, testCount);

        // 테스트 테이블의 모든 데이터 조회
        List<DrugInfoTest> testData = drugInfoTestRepository.findAll();

        // 원본 테이블 데이터를 Map으로 변환 (itemSeq 기준)
        Map<String, DrugInfo> originalMap = drugInfoRepository.findAll().stream()
                .collect(Collectors.toMap(DrugInfo::getItemSeq, Function.identity()));

        long matchedCount = 0;
        long mismatchedCount = 0;
        long newCount = 0;
        List<DrugBatchResponseDTO.MismatchDetail> mismatches = new ArrayList<>();

        for (DrugInfoTest testItem : testData) {
            DrugInfo original = originalMap.remove(testItem.getItemSeq());

            if (original == null) {
                // 테스트에만 있는 새 데이터
                newCount++;
                if (mismatches.size() < MAX_MISMATCH_DETAILS) {
                    mismatches.add(DrugBatchResponseDTO.MismatchDetail.builder()
                            .itemSeq(testItem.getItemSeq())
                            .field("NEW")
                            .originalValue(null)
                            .testValue(testItem.getItemName())
                            .build());
                }
            } else {
                // 필드별 비교
                List<DrugBatchResponseDTO.MismatchDetail> fieldMismatches =
                        compareFields(testItem, original);

                if (fieldMismatches.isEmpty()) {
                    matchedCount++;
                } else {
                    mismatchedCount++;
                    if (mismatches.size() < MAX_MISMATCH_DETAILS) {
                        mismatches.addAll(fieldMismatches.stream()
                                .limit(MAX_MISMATCH_DETAILS - mismatches.size())
                                .toList());
                    }
                }
            }
        }

        // 원본에만 남아있는 데이터 (테스트에서 누락된 데이터)
        long missingCount = originalMap.size();
        for (DrugInfo missing : originalMap.values()) {
            if (mismatches.size() >= MAX_MISMATCH_DETAILS) break;
            mismatches.add(DrugBatchResponseDTO.MismatchDetail.builder()
                    .itemSeq(missing.getItemSeq())
                    .field("MISSING")
                    .originalValue(missing.getItemName())
                    .testValue(null)
                    .build());
        }

        DrugBatchResponseDTO.VerificationSummary summary = DrugBatchResponseDTO.VerificationSummary.builder()
                .originalTableCount(originalCount)
                .testTableCount(testCount)
                .matchedCount(matchedCount)
                .mismatchedCount(mismatchedCount)
                .newCount(newCount)
                .missingCount(missingCount)
                .build();

        String status = determineVerificationStatus(summary);

        log.info("검증 완료: matched={}, mismatched={}, new={}, missing={}",
                matchedCount, mismatchedCount, newCount, missingCount);

        return DrugBatchResponseDTO.VerificationResponse.builder()
                .jobId(jobId)
                .verificationStatus(status)
                .summary(summary)
                .mismatches(mismatches)
                .verifiedAt(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional
    public void clearTestTable() {
        log.info("테스트 테이블 초기화");
        drugInfoTestRepository.deleteAll();
        log.info("테스트 테이블 초기화 완료");
    }

    @Override
    @Transactional
    public int promoteTestData() {
        log.info("테스트 데이터 운영 이관 시작");

        List<DrugInfoTest> testData = drugInfoTestRepository.findAll();
        int promotedCount = 0;

        for (DrugInfoTest test : testData) {
            DrugInfo promoted = DrugInfo.builder()
                    .itemSeq(test.getItemSeq())
                    .itemName(test.getItemName())
                    .displayName(test.getDisplayName())
                    .ingredientKr(test.getIngredientKr())
                    .entpName(test.getEntpName())
                    .efficacy(test.getEfficacy())
                    .usage(test.getUsage())
                    .precaution(test.getPrecaution())
                    .productImage(test.getProductImage())
                    .warning(test.getWarning())
                    .caution(test.getCaution())
                    .interaction(test.getInteraction())
                    .sideEffect(test.getSideEffect())
                    .storageMethod(test.getStorageMethod())
                    .imageUrl(test.getImageUrl())
                    .openDate(test.getOpenDate())
                    .apiUpdateDate(test.getApiUpdateDate())
                    .drugType(test.getDrugType())
                    .ingredientName(test.getIngredientName())
                    .permitDate(test.getPermitDate())
                    .build();

            drugInfoRepository.save(promoted);
            promotedCount++;
        }

        log.info("테스트 데이터 운영 이관 완료: {}건", promotedCount);
        return promotedCount;
    }

    // === Private Helper Methods ===

    private List<DrugBatchResponseDTO.MismatchDetail> compareFields(DrugInfoTest test, DrugInfo original) {
        List<DrugBatchResponseDTO.MismatchDetail> mismatches = new ArrayList<>();

        compareField(mismatches, test.getItemSeq(), "itemName", original.getItemName(), test.getItemName());
        compareField(mismatches, test.getItemSeq(), "displayName", original.getDisplayName(), test.getDisplayName());
        compareField(mismatches, test.getItemSeq(), "ingredientKr", original.getIngredientKr(), test.getIngredientKr());
        compareField(mismatches, test.getItemSeq(), "entpName", original.getEntpName(), test.getEntpName());
        compareField(mismatches, test.getItemSeq(), "efficacy", original.getEfficacy(), test.getEfficacy());
        compareField(mismatches, test.getItemSeq(), "usage", original.getUsage(), test.getUsage());
        compareField(mismatches, test.getItemSeq(), "warning", original.getWarning(), test.getWarning());
        compareField(mismatches, test.getItemSeq(), "caution", original.getCaution(), test.getCaution());
        compareField(mismatches, test.getItemSeq(), "interaction", original.getInteraction(), test.getInteraction());
        compareField(mismatches, test.getItemSeq(), "sideEffect", original.getSideEffect(), test.getSideEffect());
        compareField(mismatches, test.getItemSeq(), "storageMethod", original.getStorageMethod(), test.getStorageMethod());
        compareField(mismatches, test.getItemSeq(), "imageUrl", original.getImageUrl(), test.getImageUrl());

        return mismatches;
    }

    private void compareField(List<DrugBatchResponseDTO.MismatchDetail> mismatches,
                              String itemSeq, String fieldName,
                              String originalValue, String testValue) {
        if (!Objects.equals(originalValue, testValue)) {
            mismatches.add(DrugBatchResponseDTO.MismatchDetail.builder()
                    .itemSeq(itemSeq)
                    .field(fieldName)
                    .originalValue(truncateForDisplay(originalValue))
                    .testValue(truncateForDisplay(testValue))
                    .build());
        }
    }

    private String truncateForDisplay(String value) {
        if (value == null) return null;
        return value.length() > 100 ? value.substring(0, 100) + "..." : value;
    }

    private String determineVerificationStatus(DrugBatchResponseDTO.VerificationSummary summary) {
        if (summary.getMismatchedCount() == 0 && summary.getMissingCount() == 0) {
            if (summary.getNewCount() > 0) {
                return "SUCCESS_WITH_NEW_DATA";
            }
            return "SUCCESS";
        }

        double mismatchRate = (double) summary.getMismatchedCount() / summary.getOriginalTableCount();
        if (mismatchRate > 0.1) {
            return "FAILED_HIGH_MISMATCH";
        }

        if (summary.getMissingCount() > 0) {
            return "WARNING_MISSING_DATA";
        }

        return "WARNING_MISMATCH";
    }
}

package com.myyak.service.drugPdfBatchService;

import com.myyak.domain.DrugInfo;
import com.myyak.repository.DrugInfoRepository;
import com.myyak.service.excelReaderService.DrugPdfData;
import com.myyak.service.excelReaderService.ExcelReaderService;
import com.myyak.service.pdfParserService.PdfParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrugPdfBatchServiceImpl implements DrugPdfBatchService {

    private final ExcelReaderService excelReaderService;
    private final PdfParserService pdfParserService;
    private final DrugInfoRepository drugInfoRepository;

    @Override
    public BatchResult importFromExcel(String excelFilePath) {
        log.info("PDF 배치 처리 시작: {}", excelFilePath);

        List<DrugPdfData> pdfDataList = excelReaderService.readDrugPdfData(excelFilePath);
        int totalCount = pdfDataList.size();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < pdfDataList.size(); i++) {
            DrugPdfData pdfData = pdfDataList.get(i);

            try {
                boolean processed = processSingleDrug(pdfData);
                if (processed) {
                    successCount.incrementAndGet();
                } else {
                    skipCount.incrementAndGet();
                }
            } catch (Exception e) {
                log.error("처리 실패 - itemSeq: {}, error: {}", pdfData.getItemSeq(), e.getMessage());
                failCount.incrementAndGet();
            }

            // 진행 상황 로그 (1000건마다)
            if ((i + 1) % 1000 == 0) {
                log.info("진행 중: {}/{} (성공: {}, 스킵: {}, 실패: {})",
                        i + 1, totalCount, successCount.get(), skipCount.get(), failCount.get());
            }

            // Rate limiting (100ms 대기)
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("배치 처리 중단됨");
                break;
            }
        }

        BatchResult result = new BatchResult(totalCount, successCount.get(), skipCount.get(), failCount.get());
        log.info("PDF 배치 처리 완료: {}", result.toSummary());

        return result;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processSingleDrug(DrugPdfData pdfData) {
        Optional<DrugInfo> optionalDrugInfo = drugInfoRepository.findById(pdfData.getItemSeq());

        if (optionalDrugInfo.isEmpty()) {
            log.debug("DB에 존재하지 않는 품목: {}", pdfData.getItemSeq());
            return false;
        }

        DrugInfo drugInfo = optionalDrugInfo.get();

        // 각 필드가 비어있는지 확인
        boolean needEfficacy = drugInfo.getEfficacy() == null || drugInfo.getEfficacy().isBlank();
        boolean needUsage = drugInfo.getUsage() == null || drugInfo.getUsage().isBlank();
        boolean needPrecaution = drugInfo.getPrecaution() == null || drugInfo.getPrecaution().isBlank();
        boolean needStorageMethod = drugInfo.getStorageMethod() == null || drugInfo.getStorageMethod().isBlank();

        // 모든 필드가 이미 채워져 있으면 스킵
        if (!needEfficacy && !needUsage && !needPrecaution && !needStorageMethod) {
            return false;
        }

        String efficacy = null;
        String usage = null;
        String precaution = null;

        // 효능효과가 비어있을 때만 PDF 다운로드
        if (needEfficacy && isValidUrl(pdfData.getEfficacyUrl())) {
            efficacy = removeTitle(pdfParserService.extractTextFromPdfUrl(pdfData.getEfficacyUrl()), "효능효과");
        }

        // 용법용량이 비어있을 때만 PDF 다운로드
        if (needUsage && isValidUrl(pdfData.getUsageUrl())) {
            usage = removeTitle(pdfParserService.extractTextFromPdfUrl(pdfData.getUsageUrl()), "용법용량");
        }

        // 주의사항이 비어있을 때만 PDF 다운로드
        if (needPrecaution && isValidUrl(pdfData.getPrecautionUrl())) {
            precaution = removeTitle(pdfParserService.extractTextFromPdfUrl(pdfData.getPrecautionUrl()), "사용상의주의사항");
        }

        // storageMethod도 비어있을 때만 사용
        String storageMethod = needStorageMethod ? pdfData.getStorageMethod() : null;

        // 업데이트 (T컬럼은 이미지 URL 그대로)
        drugInfo.updateFromPdfData(
                efficacy,
                usage,
                precaution,
                pdfData.getProductImageUrl(),
                storageMethod
        );

        drugInfoRepository.save(drugInfo);

        log.debug("업데이트 완료: itemSeq={}", pdfData.getItemSeq());
        return true;
    }

    private boolean isValidUrl(String url) {
        return url != null && !url.isBlank() && url.startsWith("http");
    }

    private String removeTitle(String text, String title) {
        if (text == null || text.isBlank()) {
            return null;
        }
        // 맨 앞의 제목과 뒤따르는 공백/개행 제거
        return text.replaceFirst("^" + title + "\\s*", "").trim();
    }
}

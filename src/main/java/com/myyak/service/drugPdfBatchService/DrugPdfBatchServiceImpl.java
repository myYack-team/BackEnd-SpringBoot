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

        // PDF URL이 있는 경우만 처리
        if (!pdfData.hasAnyPdfUrl() && !pdfData.hasProductImageUrl() &&
            (pdfData.getStorageMethod() == null || pdfData.getStorageMethod().isBlank())) {
            return false;
        }

        String efficacy = null;
        String usage = null;
        String precaution = null;

        // Q컬럼: 전문의약품설명서 PDF에서 효능효과 추출
        if (isValidUrl(pdfData.getEfficacyUrl())) {
            efficacy = pdfParserService.extractTextFromPdfUrl(pdfData.getEfficacyUrl());
        }

        // R컬럼: 일반의약품설명서 PDF에서 용법용량 추출
        if (isValidUrl(pdfData.getUsageUrl())) {
            usage = pdfParserService.extractTextFromPdfUrl(pdfData.getUsageUrl());
        }

        // S컬럼: 일반의약품용법용량 PDF에서 사용상 주의사항 추출
        if (isValidUrl(pdfData.getPrecautionUrl())) {
            precaution = pdfParserService.extractTextFromPdfUrl(pdfData.getPrecautionUrl());
        }

        // 업데이트 (T컬럼은 이미지 URL 그대로, U컬럼은 TEXT 그대로)
        drugInfo.updateFromPdfData(
                efficacy,
                usage,
                precaution,
                pdfData.getProductImageUrl(),
                pdfData.getStorageMethod()
        );

        drugInfoRepository.save(drugInfo);

        log.debug("업데이트 완료: itemSeq={}", pdfData.getItemSeq());
        return true;
    }

    private boolean isValidUrl(String url) {
        return url != null && !url.isBlank() && url.startsWith("http");
    }
}

package com.myyak.service.excelReaderService;

import lombok.Builder;
import lombok.Getter;

/**
 * Excel에서 읽어온 약물 PDF URL 데이터
 */
@Getter
@Builder
public class DrugPdfData {

    private final String itemSeq;        // 품목일련번호
    private final String efficacyUrl;    // Q컬럼: 전문의약품설명서 PDF URL
    private final String usageUrl;       // R컬럼: 일반의약품설명서 PDF URL
    private final String precautionUrl;  // S컬럼: 일반의약품용법용량 PDF URL
    private final String productImageUrl; // T컬럼: 제품안내사항 이미지 URL
    private final String storageMethod;   // U컬럼: 보관방법 TEXT

    public boolean hasAnyPdfUrl() {
        return isValidUrl(efficacyUrl) || isValidUrl(usageUrl) || isValidUrl(precautionUrl);
    }

    public boolean hasProductImageUrl() {
        return isValidUrl(productImageUrl);
    }

    private boolean isValidUrl(String url) {
        return url != null && !url.isBlank() && url.startsWith("http");
    }
}

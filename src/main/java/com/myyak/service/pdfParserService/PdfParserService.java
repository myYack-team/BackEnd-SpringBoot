package com.myyak.service.pdfParserService;

/**
 * PDF 다운로드 및 텍스트 추출 서비스
 */
public interface PdfParserService {

    /**
     * URL에서 PDF를 다운로드하고 텍스트를 추출
     * @param pdfUrl PDF URL
     * @return 추출된 텍스트 (실패 시 null)
     */
    String extractTextFromPdfUrl(String pdfUrl);
}

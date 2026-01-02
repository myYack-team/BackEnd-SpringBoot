package com.myyak.service.drugPdfBatchService;

import java.io.InputStream;

/**
 * 약물 PDF 배치 처리 서비스
 */
public interface DrugPdfBatchService {

    /**
     * CSV 파일에서 PDF URL을 읽어 파싱하고 DB에 저장
     * @param csvFilePath CSV 파일 경로
     * @return 배치 처리 결과
     */
    BatchResult importFromExcel(String csvFilePath);

    /**
     * CSV InputStream에서 PDF URL을 읽어 파싱하고 DB에 저장 (업로드용)
     * @param inputStream CSV 파일 InputStream
     * @param fileName 파일명
     * @return 배치 처리 결과
     */
    BatchResult importFromCsv(InputStream inputStream, String fileName);

    /**
     * 배치 처리 결과
     */
    record BatchResult(
            int totalCount,
            int successCount,
            int skipCount,
            int failCount
    ) {
        public String toSummary() {
            return String.format("총 %d건 처리 (성공: %d, 스킵: %d, 실패: %d)",
                    totalCount, successCount, skipCount, failCount);
        }
    }
}

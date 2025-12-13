package com.myyak.service.excelReaderService;

import java.util.List;

/**
 * Excel 파일 읽기 서비스
 */
public interface ExcelReaderService {

    /**
     * Excel 파일에서 약물 PDF URL 데이터 읽기
     * @param filePath Excel 파일 경로
     * @return 약물 PDF 데이터 목록
     */
    List<DrugPdfData> readDrugPdfData(String filePath);
}

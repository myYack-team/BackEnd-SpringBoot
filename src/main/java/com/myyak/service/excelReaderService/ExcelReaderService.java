package com.myyak.service.excelReaderService;

import java.io.InputStream;
import java.util.List;

/**
 * CSV 파일 읽기 서비스
 */
public interface ExcelReaderService {

    /**
     * CSV 파일에서 약물 PDF URL 데이터 읽기
     * @param filePath CSV 파일 경로
     * @return 약물 PDF 데이터 목록
     */
    List<DrugPdfData> readDrugPdfData(String filePath);

    /**
     * InputStream에서 약물 PDF URL 데이터 읽기 (업로드용)
     * @param inputStream CSV 파일 InputStream
     * @param fileName 파일명 (로깅용)
     * @return 약물 PDF 데이터 목록
     */
    List<DrugPdfData> readDrugPdfData(InputStream inputStream, String fileName);
}

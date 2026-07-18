package com.myyak.service.drugBatchService;

import java.io.InputStream;

/**
 * 데이터 수집 배치 오케스트레이터
 * 여러 단계의 데이터 수집 작업을 시작하고 상태를 관리
 */
public interface DrugBatchOrchestrator {

    /**
     * 전체 동기화 작업 시작
     */
    BatchJobContext startFullSync(boolean testMode, boolean includeEfficacyCrawling,
                                  boolean includePdfImport, String pdfFilePath, Integer maxRecords);

    /**
     * 전체 동기화 작업 시작 (CSV 파일 업로드 포함)
     */
    BatchJobContext startFullSync(boolean testMode, boolean includeEfficacyCrawling,
                                  boolean includePdfImport, String pdfFilePath, Integer maxRecords,
                                  InputStream csvInputStream, String csvFileName);

    /**
     * 작업 상태 조회
     */
    BatchJobContext getJobStatus(String jobId);

    /**
     * 작업 취소 요청
     */
    boolean cancelJob(String jobId);
}

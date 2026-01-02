package com.myyak.service.drugBatchService;

/**
 * 배치 작업 진행률 콜백 인터페이스
 * 기존 서비스에서 진행 상황을 오케스트레이터에 보고할 때 사용
 */
@FunctionalInterface
public interface BatchProgressCallback {
    /**
     * 진행 상황 보고
     * @param processedCount 처리된 건수
     * @param totalCount 전체 건수 (알 수 없는 경우 null)
     * @param message 상태 메시지
     */
    void onProgress(int processedCount, Integer totalCount, String message);
}

package com.myyak.service.drugBatchService;

/**
 * 배치 작업 상태
 */
public enum BatchJobStatus {
    PENDING,        // 대기 중
    STARTED,        // 시작됨
    IN_PROGRESS,    // 진행 중
    COMPLETED,      // 완료
    FAILED,         // 실패
    CANCELLED,      // 취소됨
    RESUMING        // 재개 중
}

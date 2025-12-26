package com.myyak.domain.enums;

/**
 * 일별 복약 상태
 */
public enum IntakeDayStatus {
    NONE,       // 예정된 복약 없음
    PENDING,    // 미래 날짜 또는 오늘 복용 전
    PARTIAL,    // 일부만 복용
    COMPLETE,   // 모든 약 복용 완료
    MISSED      // 과거인데 복용 안 함
}

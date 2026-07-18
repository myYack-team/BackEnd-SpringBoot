package com.myyak.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 처방전 복용 상태
 */
@Getter
@RequiredArgsConstructor
public enum PrescriptionStatus {

    UPCOMING("복용 예정"),
    IN_PROGRESS("복용 중"),
    COMPLETED("복용 완료");

    private final String description;
}

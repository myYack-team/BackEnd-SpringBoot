package com.myyak.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IntakeStatus {

    TAKEN("복용완료"),
    SKIPPED("건너뜀"),
    MISSED("미복용");

    private final String description;
}

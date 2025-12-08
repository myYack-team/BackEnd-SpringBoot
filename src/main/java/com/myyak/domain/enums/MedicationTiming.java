package com.myyak.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalTime;

@Getter
@RequiredArgsConstructor
public enum MedicationTiming {

    BEFORE_BREAKFAST("아침 식전", LocalTime.of(7, 30)),
    AFTER_BREAKFAST("아침 식후", LocalTime.of(8, 0)),
    BEFORE_LUNCH("점심 식전", LocalTime.of(11, 30)),
    AFTER_LUNCH("점심 식후", LocalTime.of(12, 30)),
    BEFORE_DINNER("저녁 식전", LocalTime.of(17, 30)),
    AFTER_DINNER("저녁 식후", LocalTime.of(18, 30)),
    BEFORE_BED("취침 전", LocalTime.of(22, 0)),
    AS_NEEDED("필요시", null);

    private final String description;
    private final LocalTime defaultTime;
}

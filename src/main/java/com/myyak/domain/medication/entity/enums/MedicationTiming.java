package com.myyak.domain.medication.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MedicationTiming {

    MORNING_BEFORE("아침 식전"),
    MORNING_AFTER("아침 식후"),
    LUNCH_BEFORE("점심 식전"),
    LUNCH_AFTER("점심 식후"),
    DINNER_BEFORE("저녁 식전"),
    DINNER_AFTER("저녁 식후"),
    BEFORE_SLEEP("취침 전"),
    AS_NEEDED("필요시");

    private final String description;
}

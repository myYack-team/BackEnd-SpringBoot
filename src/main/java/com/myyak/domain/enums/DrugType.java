package com.myyak.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DrugType {

    PROFESSIONAL("전문의약품"),
    GENERAL("일반의약품"),
    SUPPLEMENT("영양제"),
    UNKNOWN("미분류");

    private final String description;

    public static DrugType fromApiValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        if (value.contains("전문")) {
            return PROFESSIONAL;
        }
        if (value.contains("일반")) {
            return GENERAL;
        }
        return UNKNOWN;
    }
}

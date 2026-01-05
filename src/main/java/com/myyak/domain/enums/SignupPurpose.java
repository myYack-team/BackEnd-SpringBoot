package com.myyak.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 가입 목적
 */
@Getter
@RequiredArgsConstructor
public enum SignupPurpose {
    SELF("나의 약 관리"),
    CHILD("자녀 약 관리"),
    PARENT("부모님 약 관리"),
    AI_REPORT("AI 복약 분석 레포트");

    private final String description;
}

package com.myyak.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 성별
 */
@Getter
@RequiredArgsConstructor
public enum Gender {
    MALE("male", "남성"),
    FEMALE("female", "여성");

    private final String kakaoValue;  // 카카오 API 응답값
    private final String description;

    /**
     * 카카오 API 응답값으로 Gender 조회
     */
    public static Gender fromKakaoValue(String kakaoValue) {
        if (kakaoValue == null) {
            return null;
        }
        for (Gender gender : values()) {
            if (gender.kakaoValue.equalsIgnoreCase(kakaoValue)) {
                return gender;
            }
        }
        return null;
    }
}

package com.myyak.domain.user.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FontSize {

    SMALL("작게", 14),
    MEDIUM("보통", 16),
    LARGE("크게", 18);

    private final String description;
    private final int size;
}

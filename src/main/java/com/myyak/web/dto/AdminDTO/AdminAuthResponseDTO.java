package com.myyak.web.dto.AdminDTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class AdminAuthResponseDTO {
    @Getter @AllArgsConstructor
    public static class Challenge {
        private String challenge;
        private long expiresInSeconds;
    }
    @Getter @AllArgsConstructor
    public static class Session {
        private String csrfToken;
    }
}

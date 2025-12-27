package com.myyak.web.dto.AuthDTO;

import com.myyak.domain.enums.FontSize;
import com.myyak.domain.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 인증 관련 응답 DTO
 */
public class AuthResponseDTO {

    /**
     * 로그인 응답 (토큰 + 사용자 정보)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResponse {
        private String accessToken;
        private String refreshToken;
        private Long accessTokenExpiresIn;  // 만료까지 남은 시간 (밀리초)
        private UserInfo user;
        private boolean isNewUser;  // 신규 사용자 여부 (온보딩 필요)
    }

    /**
     * 토큰 갱신 응답
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenResponse {
        private String accessToken;
        private String refreshToken;
        private Long accessTokenExpiresIn;
    }

    /**
     * 사용자 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String kakaoId;
        private String name;
        private String email;
        private String profileImage;
        private Gender gender;
        private LocalDate birthDate;
        private FontSize fontSize;
        private LocalDateTime createdAt;
    }
}

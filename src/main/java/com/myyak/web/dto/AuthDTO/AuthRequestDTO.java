package com.myyak.web.dto.AuthDTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인증 관련 요청 DTO
 */
public class AuthRequestDTO {

    /**
     * 카카오 로그인 요청
     */
    @Getter
    @NoArgsConstructor
    public static class KakaoLoginRequest {
        @NotBlank(message = "카카오 액세스 토큰은 필수입니다")
        private String accessToken;
    }

    /**
     * 토큰 갱신 요청
     */
    @Getter
    @NoArgsConstructor
    public static class RefreshRequest {
        @NotBlank(message = "리프레시 토큰은 필수입니다")
        private String refreshToken;
    }
}

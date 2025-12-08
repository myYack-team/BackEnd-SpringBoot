package com.myyak.web.dto.FcmDTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class FcmRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class RegisterToken {
        @NotBlank(message = "FCM 토큰은 필수입니다.")
        private String fcmToken;
    }
}

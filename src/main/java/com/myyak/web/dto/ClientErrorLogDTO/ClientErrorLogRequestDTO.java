package com.myyak.web.dto.ClientErrorLogDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 클라이언트 에러 로그 요청 DTO
 */
public class ClientErrorLogRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class ErrorLogRequest {
        @NotBlank(message = "에러 레벨은 필수입니다")
        @Size(max = 20)
        private String level;

        @NotBlank(message = "에러 메시지는 필수입니다")
        @Size(max = 500)
        private String message;

        private String stackTrace;

        @Size(max = 100)
        private String screen;

        @Size(max = 50)
        private String appVersion;

        @Size(max = 20)
        private String platform;

        @Size(max = 50)
        private String osVersion;

        @Size(max = 100)
        private String deviceModel;

        private String additionalInfo;
    }
}

package com.myyak.web.dto.AdminDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class AdminAuthRequestDTO {
    @Getter
    @NoArgsConstructor
    public static class VerifyCode {
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}")
        private String challenge;
        @NotBlank @Pattern(regexp = "[0-9]{9}")
        private String code;
    }
}

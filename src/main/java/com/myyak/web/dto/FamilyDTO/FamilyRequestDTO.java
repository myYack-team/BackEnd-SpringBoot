package com.myyak.web.dto.FamilyDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class FamilyRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class SendRequest {
        @NotBlank(message = "전화번호는 필수입니다")
        @Pattern(regexp = "^01[0-9]{8,9}$", message = "올바른 전화번호 형식이 아닙니다 (예: 01012345678)")
        private String phone;
    }

    @Getter
    @NoArgsConstructor
    public static class UpdateFamilyNotificationSettings {
        @NotNull(message = "가족 알림 설정 값은 필수입니다")
        private Boolean familyNotificationEnabled;
    }
}

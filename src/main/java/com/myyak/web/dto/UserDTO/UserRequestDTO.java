package com.myyak.web.dto.UserDTO;

import com.myyak.domain.enums.FontSize;
import com.myyak.domain.enums.Gender;
import com.myyak.domain.enums.SignupPurpose;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class UserRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class UpdateRequest {
        private String name;
        private FontSize fontSize;
    }

    @Getter
    @NoArgsConstructor
    public static class UpdateNotificationSettings {
        @NotNull(message = "알림 설정 값은 필수입니다")
        private Boolean notificationEnabled;
    }

    @Getter
    @NoArgsConstructor
    public static class ProfileSetupRequest {
        @NotNull(message = "성별은 필수입니다")
        private Gender gender;

        @NotNull(message = "연령대는 필수입니다")
        private String ageRange;

        @NotEmpty(message = "가입 목적은 최소 1개 이상 선택해야 합니다")
        private List<SignupPurpose> signupPurposes;
    }

    @Getter
    @NoArgsConstructor
    public static class UpdateAiConsentRequest {
        @NotNull(message = "AI 데이터 동의 여부는 필수입니다")
        private Boolean aiDataAgreed;

        private String consentVersion;
    }
}

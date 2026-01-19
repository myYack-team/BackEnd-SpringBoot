package com.myyak.web.dto.UserDTO;

import com.myyak.domain.enums.FontSize;
import com.myyak.domain.enums.Gender;
import com.myyak.domain.enums.SignupPurpose;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class UserResponseDTO {

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
        private FontSize fontSize;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateResult {
        private Long id;
        private String name;
        private FontSize fontSize;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSettings {
        private Boolean notificationEnabled;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileSetupResult {
        private Long id;
        private Gender gender;
        private String ageRange;
        private List<SignupPurpose> signupPurposes;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiConsentStatus {
        private Boolean aiDataAgreed;
        private LocalDateTime consentedAt;
        private String consentVersion;
    }

    /**
     * 서비스 이용 동의 상태 (이용약관 + 개인정보 처리방침)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsentStatus {
        private Boolean termsAgreed;
        private Boolean privacyAgreed;
        private LocalDateTime consentedAt;
        private String consentVersion;
    }
}

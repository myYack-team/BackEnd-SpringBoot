package com.myyak.converter;

import com.myyak.domain.User;
import com.myyak.domain.enums.SignupPurpose;
import com.myyak.web.dto.UserDTO.UserResponseDTO;

import java.util.List;

public class UserConverter {

    public static UserResponseDTO.UserInfo toUserInfo(User user) {
        return UserResponseDTO.UserInfo.builder()
                .id(user.getId())
                .kakaoId(user.getKakaoId())
                .name(user.getName())
                .email(user.getEmail())
                .profileImage(user.getProfileImage())
                .phone(user.getPhone())
                .fontSize(user.getFontSize())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public static UserResponseDTO.UpdateResult toUpdateResult(User user) {
        return UserResponseDTO.UpdateResult.builder()
                .id(user.getId())
                .name(user.getName())
                .fontSize(user.getFontSize())
                .build();
    }

    public static UserResponseDTO.ConsentStatus toConsentStatus(User user) {
        return UserResponseDTO.ConsentStatus.builder()
                .termsAgreed(user.getTermsAgreed())
                .privacyAgreed(user.getPrivacyAgreed())
                .consentedAt(user.getConsentedAt())
                .consentVersion(user.getConsentVersion())
                .build();
    }

    public static UserResponseDTO.NotificationSettings toNotificationSettings(User user) {
        return UserResponseDTO.NotificationSettings.builder()
                .notificationEnabled(user.getNotificationEnabled())
                .build();
    }

    public static UserResponseDTO.ProfileSetupResult toProfileSetupResult(User user, List<SignupPurpose> signupPurposes) {
        return UserResponseDTO.ProfileSetupResult.builder()
                .id(user.getId())
                .gender(user.getGender())
                .ageRange(user.getAgeRange())
                .signupPurposes(signupPurposes)
                .build();
    }

    public static UserResponseDTO.AiConsentStatus toAiConsentStatus(User user) {
        return UserResponseDTO.AiConsentStatus.builder()
                .aiDataAgreed(user.getAiDataAgreed())
                .consentedAt(user.getConsentedAt())
                .consentVersion(user.getConsentVersion())
                .build();
    }

    public static UserResponseDTO.PhoneUpdateResult toPhoneUpdateResult(User user) {
        return UserResponseDTO.PhoneUpdateResult.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .build();
    }
}

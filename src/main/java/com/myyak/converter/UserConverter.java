package com.myyak.converter;

import com.myyak.domain.User;
import com.myyak.web.dto.UserDTO.UserResponseDTO;

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
}

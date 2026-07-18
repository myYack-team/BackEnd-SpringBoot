package com.myyak.converter;

import com.myyak.domain.User;
import com.myyak.web.dto.AuthDTO.AuthResponseDTO;

/**
 * 인증 응답 Converter
 */
public class AuthConverter {

    /**
     * 로그인 응답 DTO 조립 (토큰 + 사용자 정보 + 약관 동의 여부)
     */
    public static AuthResponseDTO.LoginResponse toLoginResponse(
            String accessToken, String refreshToken, Long accessTokenExpiresIn,
            User user, boolean isNewUser) {
        return AuthResponseDTO.LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(accessTokenExpiresIn)
                .user(toUserInfo(user))
                .isNewUser(isNewUser)
                .termsAgreed(user.getTermsAgreed())
                .privacyAgreed(user.getPrivacyAgreed())
                .build();
    }

    /**
     * 토큰 갱신 응답 DTO 조립
     */
    public static AuthResponseDTO.TokenResponse toTokenResponse(
            String accessToken, String refreshToken, Long accessTokenExpiresIn) {
        return AuthResponseDTO.TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(accessTokenExpiresIn)
                .build();
    }

    /**
     * 사용자 엔티티 → 인증 응답용 사용자 정보 DTO
     */
    public static AuthResponseDTO.UserInfo toUserInfo(User user) {
        return AuthResponseDTO.UserInfo.builder()
                .id(user.getId())
                .kakaoId(user.getKakaoId())
                .name(user.getName())
                .email(user.getEmail())
                .profileImage(user.getProfileImage())
                .gender(user.getGender())
                .ageRange(user.getAgeRange())
                .signupPurposes(user.getSignupPurposes())
                .fontSize(user.getFontSize())
                .createdAt(user.getCreatedAt())
                .build();
    }
}

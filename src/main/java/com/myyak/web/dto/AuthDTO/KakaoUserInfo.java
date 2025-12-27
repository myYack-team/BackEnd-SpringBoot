package com.myyak.web.dto.AuthDTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 카카오 사용자 정보 응답 DTO
 * https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api#req-user-info
 */
@Getter
@NoArgsConstructor
@ToString
public class KakaoUserInfo {

    private Long id;  // 카카오 회원번호

    @JsonProperty("kakao_account")
    private KakaoAccount kakaoAccount;

    @Getter
    @NoArgsConstructor
    @ToString
    public static class KakaoAccount {
        private String email;

        @JsonProperty("email_needs_agreement")
        private Boolean emailNeedsAgreement;

        @JsonProperty("is_email_valid")
        private Boolean isEmailValid;

        @JsonProperty("is_email_verified")
        private Boolean isEmailVerified;

        private Profile profile;

        @JsonProperty("profile_nickname_needs_agreement")
        private Boolean profileNicknameNeedsAgreement;

        @JsonProperty("profile_image_needs_agreement")
        private Boolean profileImageNeedsAgreement;

        private String gender;  // male, female

        @JsonProperty("gender_needs_agreement")
        private Boolean genderNeedsAgreement;

        private String birthday;  // MMDD 형식

        @JsonProperty("birthday_needs_agreement")
        private Boolean birthdayNeedsAgreement;

        @JsonProperty("birthday_type")
        private String birthdayType;  // SOLAR, LUNAR

        private String birthyear;  // YYYY 형식

        @JsonProperty("birthyear_needs_agreement")
        private Boolean birthyearNeedsAgreement;
    }

    @Getter
    @NoArgsConstructor
    @ToString
    public static class Profile {
        private String nickname;

        @JsonProperty("thumbnail_image_url")
        private String thumbnailImageUrl;

        @JsonProperty("profile_image_url")
        private String profileImageUrl;

        @JsonProperty("is_default_image")
        private Boolean isDefaultImage;
    }
}

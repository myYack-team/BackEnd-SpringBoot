package com.myyak.web.dto.UserDTO;

import com.myyak.domain.enums.FontSize;
import com.myyak.domain.enums.Gender;
import com.myyak.domain.enums.SignupPurpose;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class UserRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class UpdateRequest {
        @Size(min = 2, max = 20, message = "이름은 2~20자 사이여야 합니다")
        @Pattern(regexp = "^[가-힣a-zA-Z0-9]*$", message = "이름에 특수문자나 공백을 사용할 수 없습니다")
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

    /**
     * 서비스 이용 동의 요청 (이용약관 + 개인정보 처리방침)
     */
    @Getter
    @NoArgsConstructor
    public static class ConsentRequest {
        @NotNull(message = "이용약관 동의 여부는 필수입니다")
        private Boolean termsAgreed;

        @NotNull(message = "개인정보 처리방침 동의 여부는 필수입니다")
        private Boolean privacyAgreed;

        private String consentVersion;
    }

    /**
     * 전화번호 등록/수정 요청
     */
    @Getter
    @NoArgsConstructor
    public static class UpdatePhoneRequest {
        @NotBlank(message = "전화번호는 필수입니다")
        @Pattern(regexp = "^01[0-9]{8,9}$", message = "올바른 전화번호 형식이 아닙니다 (예: 01012345678)")
        private String phone;
    }
}

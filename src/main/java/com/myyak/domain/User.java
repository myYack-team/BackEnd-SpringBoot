package com.myyak.domain;

import com.myyak.converter.EncryptedStringConverter;
import com.myyak.domain.common.BaseEntity;
import com.myyak.domain.enums.FontSize;
import com.myyak.domain.enums.Gender;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;


@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_name_unique", columnList = "name", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String kakaoId;

    @Column(nullable = false)
    private String name;

    @Convert(converter = EncryptedStringConverter.class)
    private String email;

    private String profileImage;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private String ageRange;

    private String signupPurposes;

    @Convert(converter = EncryptedStringConverter.class)
    private String phone;

    @Column(unique = true)
    private String phoneHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FontSize fontSize = FontSize.LARGE;

    @Convert(converter = EncryptedStringConverter.class)
    private String fcmToken;

    @Column(nullable = false)
    @Builder.Default
    private Boolean notificationEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean familyNotificationEnabled = true;

    // 약관 동의 관련 필드
    @Column(nullable = false)
    @Builder.Default
    private Boolean termsAgreed = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean privacyAgreed = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean aiDataAgreed = false;

    private LocalDateTime consentedAt;

    @Column(length = 20)
    private String consentVersion;

    public void updateProfile(String name, String profileImage) {
        this.name = name;
        this.profileImage = profileImage;
    }

    public void updateKakaoInfo(String name, String email, String profileImage) {
        this.name = name;
        if (email != null) {
            this.email = email;
        }
        this.profileImage = profileImage;
    }

    public void updateProfileSetup(Gender gender, String ageRange, String signupPurposes) {
        this.gender = gender;
        this.ageRange = ageRange;
        this.signupPurposes = signupPurposes;
    }

    public void updateFontSize(FontSize fontSize) {
        this.fontSize = fontSize;
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public void updateNotificationEnabled(Boolean notificationEnabled) {
        this.notificationEnabled = notificationEnabled;
    }

    public void updateConsent(Boolean termsAgreed, Boolean privacyAgreed, Boolean aiDataAgreed, String consentVersion) {
        this.termsAgreed = termsAgreed;
        this.privacyAgreed = privacyAgreed;
        this.aiDataAgreed = aiDataAgreed;
        this.consentVersion = consentVersion;
        this.consentedAt = LocalDateTime.now();
    }

    public void updateAiConsent(Boolean aiDataAgreed, String consentVersion) {
        this.aiDataAgreed = aiDataAgreed;
        if (consentVersion != null) {
            this.consentVersion = consentVersion;
        }
        this.consentedAt = LocalDateTime.now();
    }

    public void updatePhone(String phone, String phoneHash) {
        this.phone = phone;
        this.phoneHash = phoneHash;
    }

    public void updateFamilyNotificationEnabled(Boolean familyNotificationEnabled) {
        this.familyNotificationEnabled = familyNotificationEnabled;
    }
}

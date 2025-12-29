package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import com.myyak.domain.enums.FontSize;
import com.myyak.domain.enums.Gender;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "users")
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

    private String email;

    private String profileImage;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FontSize fontSize = FontSize.LARGE;

    private String fcmToken;

    @Column(nullable = false)
    @Builder.Default
    private Boolean notificationEnabled = true;

    public void updateProfile(String name, String profileImage) {
        this.name = name;
        this.profileImage = profileImage;
    }

    public void updateKakaoInfo(String name, String email, String profileImage, Gender gender, LocalDate birthDate) {
        this.name = name;
        if (email != null) {
            this.email = email;
        }
        this.profileImage = profileImage;
        if (gender != null) {
            this.gender = gender;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
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
}

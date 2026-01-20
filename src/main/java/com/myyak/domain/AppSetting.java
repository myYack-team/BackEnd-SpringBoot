package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 애플리케이션 설정 엔티티
 * Key-Value 형태로 설정값 저장
 */
@Entity
@Table(name = "app_settings",
        indexes = @Index(name = "idx_app_setting_key", columnList = "setting_key"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AppSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 100)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 500)
    private String settingValue;

    @Column(length = 200)
    private String description;

    /**
     * 설정값 업데이트
     */
    public void updateValue(String value) {
        this.settingValue = value;
    }

    // ===== 설정 키 상수 =====
    public static final String KEY_GEMINI_ANALYSIS_MODEL = "gemini.analysis.model";
    public static final String KEY_GEMINI_ANALYSIS_FALLBACK_MODEL = "gemini.analysis.fallback-model";
    public static final String KEY_GEMINI_FALLBACK_ENABLED = "gemini.analysis.fallback-enabled";
}

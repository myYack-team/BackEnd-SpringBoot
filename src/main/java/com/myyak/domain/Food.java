package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import com.myyak.domain.enums.FoodCategory;
import jakarta.persistence.*;
import lombok.*;

/**
 * 음식/영양소 마스터 테이블
 * AI 약물 분석 시 음식-약물 상호작용 정보 제공용
 */
@Entity
@Table(name = "food")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Food extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;                    // 음식명 (한글)

    @Column(length = 200)
    private String nameEn;                  // 영문명

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FoodCategory category;          // FOOD, BEVERAGE, NUTRIENT, SUPPLEMENT, HERB

    @Column(length = 10)
    private String iconEmoji;               // 이모지 아이콘

    @Column(columnDefinition = "TEXT")
    private String description;             // 설명

    @Column(columnDefinition = "TEXT")
    private String commonAliases;           // 다른 이름들 (JSON array)
}

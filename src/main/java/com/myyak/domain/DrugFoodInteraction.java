package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import com.myyak.domain.enums.InteractionLevel;
import jakarta.persistence.*;
import lombok.*;

/**
 * 약-음식 병용 정보 테이블
 * AI 약물 분석 시 약물-음식 상호작용 정보 제공용
 */
@Entity
@Table(name = "drug_food_interaction",
       uniqueConstraints = @UniqueConstraint(columnNames = {"drug_item_seq", "food_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DrugFoodInteraction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drug_item_seq", length = 20)
    private String drugItemSeq;             // 약물 품목기준코드 (nullable - 성분 기반 매칭 시)

    @Column(length = 200)
    private String ingredientName;          // 성분명 (성분 기반 매칭용)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_id")
    private Food food;                      // 음식 FK

    @Column(length = 200)
    private String foodName;                // 음식명 (유연한 매칭용, food_id 없을 때)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InteractionLevel interactionLevel;  // HIGH, MEDIUM, LOW

    @Column(columnDefinition = "JSON")
    private String interactionData;         // 병용 정보 JSON
    /*
    {
        "reason": "자몽이 이 약의 대사를 늦출 수 있습니다",
        "mechanism": "자몽의 성분이 약물 분해 효소의 작용을 억제",
        "context": "체내 약물 농도가 높아질 수 있습니다"
    }
    */

    @Column(nullable = false, length = 50)
    private String dataSource;              // 데이터 출처 (MFDS, LITERATURE, MANUAL)

    @Column(length = 500)
    private String sourceReference;         // 출처 URL 또는 문서 번호
}

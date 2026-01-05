package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 약-약 병용 정보 테이블
 * AI 약물 분석 시 약물 간 상호작용 정보 제공용
 */
@Entity
@Table(name = "drug_interaction",
       uniqueConstraints = @UniqueConstraint(columnNames = {"drug_a_item_seq", "drug_b_item_seq"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DrugInteraction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drug_a_item_seq", nullable = false, length = 20)
    private String drugAItemSeq;            // 약물 A 품목기준코드

    @Column(name = "drug_b_item_seq", nullable = false, length = 20)
    private String drugBItemSeq;            // 약물 B 품목기준코드

    @Column(columnDefinition = "JSON")
    private String interactionData;         // 병용 정보 JSON
    /*
    {
        "reason": "두 약물 모두 혈압을 낮추는 작용을 합니다",
        "mechanism": "혈관 이완 효과가 중복됨",
        "context": "함께 복용 시 어지러움을 느낄 수 있습니다"
    }
    */

    @Column(nullable = false, length = 50)
    private String dataSource;              // 데이터 출처 (MFDS_DUR, MFDS_API, MANUAL)

    @Column(length = 500)
    private String sourceReference;         // 출처 URL 또는 문서 번호
}

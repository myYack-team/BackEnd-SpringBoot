package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import com.myyak.domain.enums.SupplementTag;
import jakarta.persistence.*;
import lombok.*;

/**
 * 영양제 마스터 정보 테이블
 * 사용자가 직접 등록하고 다른 사용자들이 검색/선택할 수 있음
 */
@Entity
@Table(name = "supplements", indexes = {
    @Index(name = "idx_supplement_name", columnList = "name"),
    @Index(name = "idx_supplement_tag", columnList = "tag"),
    @Index(name = "idx_supplement_created_by", columnList = "created_by_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Supplement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;  // 영양제명

    @Column(columnDefinition = "TEXT")
    private String description;  // 설명

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupplementTag tag;  // 영양제 태그 (비타민C, 오메가3 등)

    @Column(length = 500)
    private String imageUrl;  // 영양제 이미지 URL

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;  // 등록한 사용자

    @Builder.Default
    @Column(nullable = false)
    private Integer selectionCount = 0;  // 선택 횟수 (인기도)

    public void incrementSelectionCount() {
        this.selectionCount++;
    }

    public void decrementSelectionCount() {
        if (this.selectionCount > 0) {
            this.selectionCount--;
        }
    }

    public void update(String name, String description, SupplementTag tag, String imageUrl) {
        this.name = name;
        this.description = description;
        this.tag = tag;
        this.imageUrl = imageUrl;
    }
}

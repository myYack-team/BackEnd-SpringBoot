package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 사용자-영양제 연결 테이블
 * 사용자의 영양제 복용 정보 (복용량, 횟수, 메모 등)
 */
@Entity
@Table(name = "user_supplements", indexes = {
    @Index(name = "idx_user_supp_user", columnList = "user_id"),
    @Index(name = "idx_user_supp_supplement", columnList = "supplement_id"),
    @Index(name = "idx_user_supp_active", columnList = "isActive")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserSupplement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplement_id", nullable = false)
    private Supplement supplement;

    // 복용량 (예: "1정", "2캡슐")
    @Column(nullable = false, length = 50)
    private String dosage;

    // 하루 복용 횟수
    @Column(nullable = false)
    private Integer frequency;

    // 복용 시작일
    @Column(nullable = false)
    private LocalDate startDate;

    // 복용 종료 예정일 (영양제는 null일 수 있음 - 계속 복용)
    private LocalDate endDate;

    // 활성 상태
    @Builder.Default
    private Boolean isActive = true;

    // 메모
    @Column(length = 500)
    private String memo;

    /**
     * 영양제 이름 반환
     */
    public String getSupplementName() {
        return supplement != null ? supplement.getName() : null;
    }

    public void update(String dosage, Integer frequency, LocalDate endDate, String memo) {
        this.dosage = dosage;
        this.frequency = frequency;
        this.endDate = endDate;
        this.memo = memo;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }
}

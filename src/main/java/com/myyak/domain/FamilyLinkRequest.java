package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import com.myyak.domain.enums.FamilyLinkRequestStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "family_link_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FamilyLinkRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    private User target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FamilyLinkRequestStatus status = FamilyLinkRequestStatus.PENDING;

    public void updateStatus(FamilyLinkRequestStatus status) {
        this.status = status;
    }
}

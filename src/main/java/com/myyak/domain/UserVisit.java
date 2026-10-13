package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "user_visits", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_visits_user_date", columnNames = {"user_id", "visit_date"}),
        indexes = @Index(name = "idx_user_visits_date", columnList = "visit_date"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserVisit extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_visits_user"))
    private User user;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;
}

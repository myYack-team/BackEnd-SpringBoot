package com.myyak.repository;

import com.myyak.domain.QnA;
import com.myyak.domain.enums.QnAStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QnARepository extends JpaRepository<QnA, Long> {

    Page<QnA> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<QnA> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<QnA> findByStatusOrderByCreatedAtDesc(QnAStatus status, Pageable pageable);

    long countByStatus(QnAStatus status);

    /**
     * 회원 탈퇴 시 사용자의 모든 문의 일괄 삭제
     */
    @Modifying
    @Query("DELETE FROM QnA q WHERE q.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}

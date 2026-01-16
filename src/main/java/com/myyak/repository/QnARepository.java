package com.myyak.repository;

import com.myyak.domain.QnA;
import com.myyak.domain.enums.QnAStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QnARepository extends JpaRepository<QnA, Long> {

    Page<QnA> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<QnA> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<QnA> findByStatusOrderByCreatedAtDesc(QnAStatus status, Pageable pageable);

    long countByStatus(QnAStatus status);
}

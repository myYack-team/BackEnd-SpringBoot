package com.myyak.repository;

import com.myyak.domain.User;
import com.myyak.domain.UserAnalysisQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserAnalysisQuotaRepository extends JpaRepository<UserAnalysisQuota, Long> {

    /**
     * 사용자의 분석 쿼터 조회
     */
    Optional<UserAnalysisQuota> findByUser(User user);

    /**
     * 사용자 ID로 분석 쿼터 조회
     */
    Optional<UserAnalysisQuota> findByUserId(Long userId);

    /**
     * 회원 탈퇴 시 사용자의 분석 쿼터 일괄 삭제
     */
    @Modifying
    @Query("DELETE FROM UserAnalysisQuota q WHERE q.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}

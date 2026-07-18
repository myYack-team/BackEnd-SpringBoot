package com.myyak.repository;

import com.myyak.domain.HealthNote;
import com.myyak.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HealthNoteRepository extends JpaRepository<HealthNote, Long> {

    /**
     * 특정 사용자의 특정 날짜 메모 조회
     */
    Optional<HealthNote> findByUserAndNoteDate(User user, LocalDate noteDate);

    /**
     * 특정 사용자의 날짜 범위 메모 조회 (오래된 순)
     */
    @Query("SELECT hn FROM HealthNote hn WHERE hn.user = :user AND hn.noteDate BETWEEN :startDate AND :endDate ORDER BY hn.noteDate ASC")
    List<HealthNote> findByUserAndNoteDateBetween(
            @Param("user") User user,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 특정 사용자의 특정 날짜 메모 존재 여부 확인
     */
    boolean existsByUserAndNoteDate(User user, LocalDate noteDate);

    /**
     * 특정 사용자의 특정 날짜 메모 삭제
     */
    void deleteByUserAndNoteDate(User user, LocalDate noteDate);

    /**
     * 사용자 ID로 날짜 범위 메모 조회 (패턴 분석용)
     */
    @Query("SELECT hn FROM HealthNote hn WHERE hn.user.id = :userId AND hn.noteDate BETWEEN :startDate AND :endDate ORDER BY hn.noteDate ASC")
    List<HealthNote> findByUserIdAndNoteDateBetween(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 특정 사용자의 일정 날짜 이후 건강 메모 수 (데이터 충분성 확인용)
     */
    @Query("SELECT COUNT(hn) FROM HealthNote hn WHERE hn.user.id = :userId AND hn.noteDate > :afterDate")
    int countByUserIdAndNoteDateAfter(
            @Param("userId") Long userId,
            @Param("afterDate") LocalDate afterDate
    );

    /**
     * 회원 탈퇴 시 사용자의 모든 건강 노트 일괄 삭제
     */
    @Modifying
    @Query("DELETE FROM HealthNote h WHERE h.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}

package com.myyak.repository;

import com.myyak.domain.Reminder;
import com.myyak.domain.UserMedication;
import com.myyak.domain.UserSupplement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    // ============ UserMedication 관련 ============

    List<Reminder> findByUserMedication(UserMedication userMedication);

    // 여러 UserMedication의 리마인더를 한 번에 조회 (N+1 방지)
    @Query("SELECT r FROM Reminder r WHERE r.userMedication IN :medications")
    List<Reminder> findByUserMedicationIn(@Param("medications") List<UserMedication> medications);

    void deleteByUserMedication(UserMedication userMedication);

    @Query("SELECT r FROM Reminder r " +
           "LEFT JOIN FETCH r.userMedication um LEFT JOIN FETCH um.drugInfo " +
           "LEFT JOIN FETCH r.userSupplement us LEFT JOIN FETCH us.supplement " +
           "WHERE ((um IS NOT NULL AND um.user.id = :userId AND um.isActive = true " +
           "        AND um.remainingCount > 0 AND (um.endDate IS NULL OR um.endDate > CURRENT_DATE)) " +
           "  OR (us IS NOT NULL AND us.user.id = :userId AND us.isActive = true))")
    List<Reminder> findByUserId(@Param("userId") Long userId);

    /**
     * 약물 + 영양제 리마인더 통합 조회 (오늘의 복약, 복용 달력에서 사용)
     * UserMedication, UserSupplement, DrugInfo, Supplement 모두 Fetch Join
     */
    @Query("SELECT r FROM Reminder r " +
           "LEFT JOIN FETCH r.userMedication um LEFT JOIN FETCH um.drugInfo " +
           "LEFT JOIN FETCH r.userSupplement us LEFT JOIN FETCH us.supplement " +
           "WHERE r.enabled = true " +
           "AND ((um IS NOT NULL AND um.user.id = :userId AND um.isActive = true " +
           "        AND um.remainingCount > 0 AND (um.endDate IS NULL OR um.endDate > CURRENT_DATE)) " +
           "  OR (us IS NOT NULL AND us.user.id = :userId AND us.isActive = true))")
    List<Reminder> findAllEnabledByUserIdWithDetails(@Param("userId") Long userId);

    /**
     * 약물 + 영양제 리마인더 통합 조회 (비활성 포함, 캘린더/히스토리용)
     * isActive 필터 없이 모든 enabled 리마인더 조회 → 날짜 범위로 필터링
     */
    @Query("SELECT r FROM Reminder r " +
           "LEFT JOIN FETCH r.userMedication um LEFT JOIN FETCH um.drugInfo " +
           "LEFT JOIN FETCH r.userSupplement us LEFT JOIN FETCH us.supplement " +
           "WHERE r.enabled = true " +
           "AND ((um IS NOT NULL AND um.user.id = :userId) " +
           "  OR (us IS NOT NULL AND us.user.id = :userId))")
    List<Reminder> findAllEnabledByUserIdWithDetailsIncludingInactive(@Param("userId") Long userId);

    // ============ UserSupplement 관련 ============

    List<Reminder> findByUserSupplement(UserSupplement userSupplement);

    // 여러 UserSupplement의 리마인더를 한 번에 조회 (N+1 방지)
    @Query("SELECT r FROM Reminder r WHERE r.userSupplement IN :supplements")
    List<Reminder> findByUserSupplementIn(@Param("supplements") List<UserSupplement> supplements);

    void deleteByUserSupplement(UserSupplement userSupplement);

    // ============ 통합 쿼리 ============

    /**
     * 특정 시간의 활성화된 알림 조회 (User, DrugInfo, Supplement까지 Fetch Join)
     * 스케줄러에서 사용 - LazyInitializationException 방지
     */
    @Query("SELECT r FROM Reminder r " +
           "LEFT JOIN FETCH r.userMedication um LEFT JOIN FETCH um.user LEFT JOIN FETCH um.drugInfo " +
           "LEFT JOIN FETCH r.userSupplement us LEFT JOIN FETCH us.user LEFT JOIN FETCH us.supplement " +
           "WHERE r.time = :time AND r.enabled = true " +
           "AND ((um IS NOT NULL AND um.isActive = true " +
           "        AND um.remainingCount > 0 AND (um.endDate IS NULL OR um.endDate > CURRENT_DATE)) " +
           "  OR (us IS NOT NULL AND us.isActive = true))")
    List<Reminder> findAllActiveByTimeAndEnabledTrue(@Param("time") LocalTime time);
}

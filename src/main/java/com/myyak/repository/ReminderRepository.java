package com.myyak.repository;

import com.myyak.domain.Reminder;
import com.myyak.domain.UserMedication;
import com.myyak.domain.UserSupplement;
import com.myyak.domain.enums.MedicationTiming;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    // ============ UserMedication 관련 ============

    List<Reminder> findByUserMedication(UserMedication userMedication);

    // 여러 UserMedication의 리마인더를 한 번에 조회 (N+1 방지)
    @Query("SELECT r FROM Reminder r WHERE r.userMedication IN :medications")
    List<Reminder> findByUserMedicationIn(@Param("medications") List<UserMedication> medications);

    void deleteByUserMedication(UserMedication userMedication);

    @Query("SELECT r FROM Reminder r JOIN r.userMedication um WHERE um.user.id = :userId AND um.isActive = true")
    List<Reminder> findByUserId(@Param("userId") Long userId);

    @Query("SELECT r FROM Reminder r JOIN r.userMedication um WHERE um.user.id = :userId AND r.enabled = true AND um.isActive = true")
    List<Reminder> findEnabledByUserId(@Param("userId") Long userId);

    // N+1 방지: UserMedication을 함께 조회
    @Query("SELECT r FROM Reminder r JOIN FETCH r.userMedication um WHERE um.user.id = :userId AND r.enabled = true AND um.isActive = true")
    List<Reminder> findEnabledByUserIdWithMedication(@Param("userId") Long userId);

    @Query("SELECT r FROM Reminder r JOIN r.userMedication um WHERE r.time = :time AND r.enabled = true AND um.isActive = true")
    List<Reminder> findByTimeAndEnabledTrue(@Param("time") LocalTime time);

    Optional<Reminder> findByUserMedicationAndTiming(UserMedication userMedication, MedicationTiming timing);

    // ============ UserSupplement 관련 ============

    List<Reminder> findByUserSupplement(UserSupplement userSupplement);

    // 여러 UserSupplement의 리마인더를 한 번에 조회 (N+1 방지)
    @Query("SELECT r FROM Reminder r WHERE r.userSupplement IN :supplements")
    List<Reminder> findByUserSupplementIn(@Param("supplements") List<UserSupplement> supplements);

    void deleteByUserSupplement(UserSupplement userSupplement);

    @Query("SELECT r FROM Reminder r JOIN r.userSupplement us WHERE us.user.id = :userId AND us.isActive = true")
    List<Reminder> findSupplementRemindersByUserId(@Param("userId") Long userId);

    @Query("SELECT r FROM Reminder r JOIN r.userSupplement us WHERE us.user.id = :userId AND r.enabled = true AND us.isActive = true")
    List<Reminder> findEnabledSupplementRemindersByUserId(@Param("userId") Long userId);

    Optional<Reminder> findByUserSupplementAndTiming(UserSupplement userSupplement, MedicationTiming timing);

    // ============ 통합 쿼리 ============

    /**
     * 특정 시간의 활성화된 알림 조회 (User, DrugInfo, Supplement까지 Fetch Join)
     * 스케줄러에서 사용 - LazyInitializationException 방지
     */
    @Query("SELECT r FROM Reminder r " +
           "LEFT JOIN FETCH r.userMedication um LEFT JOIN FETCH um.user LEFT JOIN FETCH um.drugInfo " +
           "LEFT JOIN FETCH r.userSupplement us LEFT JOIN FETCH us.user LEFT JOIN FETCH us.supplement " +
           "WHERE r.time = :time AND r.enabled = true " +
           "AND ((um IS NOT NULL AND um.isActive = true) OR (us IS NOT NULL AND us.isActive = true))")
    List<Reminder> findAllActiveByTimeAndEnabledTrue(@Param("time") LocalTime time);
}
